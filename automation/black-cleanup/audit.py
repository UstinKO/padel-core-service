"""Ограниченная сверка согласованных учебных мероприятий, без изменения БД."""
import json
import subprocess

SQL = r"""
BEGIN READ ONLY;
SET LOCAL statement_timeout = '30s';
WITH target AS (SELECT * FROM tournaments_db WHERE id IN (191,193)),
regs AS (SELECT * FROM tournament_registrations_db WHERE tournament_id IN (191,193)),
people AS (SELECT player_id AS id FROM regs UNION SELECT partner_id FROM regs WHERE partner_id IS NOT NULL),
tables AS (
 SELECT c.relname AS name,
   (SELECT jsonb_agg(jsonb_build_object('name',a.attname,'type',format_type(a.atttypid,a.atttypmod),'nullable',NOT a.attnotnull) ORDER BY a.attnum)
    FROM pg_attribute a WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped) AS columns
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r'
), fks AS (
 SELECT c.conname AS name,c.conrelid::regclass::text AS child,c.confrelid::regclass::text AS parent,
 pg_get_constraintdef(c.oid) AS definition,c.condeferrable AS deferrable
 FROM pg_constraint c WHERE c.contype='f' AND c.connamespace='public'::regnamespace
)
SELECT jsonb_build_object(
 'captured_at',transaction_timestamp(),
 'database_bytes',pg_database_size(current_database()),
 'owner',(SELECT jsonb_build_object('id',id,'role',role,'active',is_active,'club_id',club_id) FROM owners_db WHERE id=5),
 'owner_clubs',(SELECT jsonb_agg(club_id ORDER BY club_id) FROM owner_club_access_db WHERE owner_id=5),
 'owner_events',(SELECT jsonb_agg(jsonb_build_object('id',id,'club_id',club_id,'active',is_active,'status',estado,'test',is_test_tournament,'date',fecha_inicio) ORDER BY id) FROM tournaments_db WHERE owner_id=5),
 'targets',(SELECT jsonb_agg(jsonb_build_object('id',id,'owner_id',owner_id,'club_id',club_id,'active',is_active,'status',estado,'test',is_test_tournament,'date',fecha_inicio) ORDER BY id) FROM target),
 'registrations',(SELECT jsonb_agg(jsonb_build_object('id',id,'event_id',tournament_id,'player_id',player_id,'partner_id',partner_id,'status',status,'active',is_active) ORDER BY id) FROM regs),
 'profiles',(SELECT jsonb_agg(jsonb_build_object('id',p.id,'active',p.activo,'guest',p.email LIKE '%@1padel.guest',
    'test_name',lower(p.nombre) LIKE 'test%' OR p.nombre LIKE 'QA189-%','test_email',lower(p.email) LIKE 'test%' OR p.email LIKE '%@example.test',
    'created_at',to_jsonb(p)->>'created_at','last_login_at',to_jsonb(p)->>'last_login_at',
    'other_registration_events',(SELECT jsonb_agg(DISTINCT r.tournament_id ORDER BY r.tournament_id) FROM tournament_registrations_db r WHERE (r.player_id=p.id OR r.partner_id=p.id) AND r.tournament_id NOT IN (191,193))) ORDER BY p.id)
   FROM player_padel_db p JOIN people ON people.id=p.id),
 'payments',(SELECT jsonb_agg(jsonb_build_object('id',p.id,'registration_id',p.registration_id,'amount',p.amount,'currency',p.currency,'status',p.status,'active',p.is_active) ORDER BY p.id) FROM payments_db p JOIN regs r ON r.id=p.registration_id),
 'tables',(SELECT jsonb_agg(to_jsonb(t) ORDER BY name) FROM tables t),
 'foreign_keys',(SELECT jsonb_agg(to_jsonb(f) ORDER BY name) FROM fks f),
 'triggers',(SELECT jsonb_agg(jsonb_build_object('table',t.tgrelid::regclass::text,'name',t.tgname,'definition',pg_get_triggerdef(t.oid))) FROM pg_trigger t WHERE NOT t.tgisinternal AND t.tgrelid IN (SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace))
);
COMMIT;
"""


def query(sql):
    command = ['docker', 'exec', '-i', 'padel-postgres', 'sh', '-ec',
               'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
    result = subprocess.run(command, input=sql, check=True, capture_output=True, text=True)
    return json.loads(next(line for line in result.stdout.splitlines() if line.startswith('{')))


if __name__ == '__main__':
    print('BLACK_AUDIT_PYTHON_STARTED', flush=True)
    try:
        data = query(SQL)
        assert data['owner']['id'] == 5 and data['owner']['role'] == 'CLUB_ADMIN' and data['owner']['active']
        assert {r['id'] for r in data['targets']} == {191, 193}
        assert all(r['owner_id'] == 5 for r in data['targets'])
        assert len(data['registrations']) == 52 and len(data['profiles']) == 52
        print('BLACK_CLEANUP_AUDIT=' + json.dumps(data, ensure_ascii=True, separators=(',', ':')))
    except subprocess.CalledProcessError as error:
        print('Сверка не выполнена: ошибка SQL, код ' + str(error.returncode))
        raise SystemExit(1)
    except (AssertionError, KeyError, StopIteration, ValueError) as error:
        print('Сверка не выполнена: состав данных отличается от ожидаемого (' + type(error).__name__ + ')')
        raise SystemExit(1)
