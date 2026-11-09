import React, { useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import type { ReplacementCandidate } from './auth';
import type { PartnerCandidate } from './pairBooking';
import { pairCopy } from './pairCopy';
import { pairExitCopy } from './pairTerms';
import ReplacementSearchField from './ReplacementSearchField';
import type { Language } from './schedule';
import { colors } from './theme';

export default function PairReplacementForm({ language, busy, search, onSubmit, submitLabel }: {
  language: Language; busy: boolean; search: (query: string) => Promise<unknown>;
  onSubmit: (candidate: ReplacementCandidate) => void; submitLabel?: string;
}) {
  const t = pairExitCopy[language];
  const [replacement, setReplacement] = useState({ nombre: '', apellido: '', telefono: '' });
  const [selected, setSelected] = useState<PartnerCandidate | null>(null);
  const fields = [
    { key: 'nombre', label: t.first },
    { key: 'apellido', label: t.last },
    { key: 'telefono', label: t.phone },
  ] as const;
  const nombre = replacement.nombre.trim();
  const apellido = replacement.apellido.trim();
  const telefono = replacement.telefono.trim();
  const validPhone = /^[+]?[0-9 ()-]{8,19}$/.test(telefono) && telefono.replace(/[^0-9]/g, '').length >= 8;
  const candidate = selected
    ? { nombre: selected.nombre, apellido: selected.apellido, existingUserId: selected.id }
    : nombre && apellido && validPhone ? { nombre, apellido, telefono } : null;

  return <View style={styles.form}>
    {selected ? <View style={styles.selection}>
      <Text style={styles.text}>{t.selectedPlayer}</Text>
      <Text style={styles.name}>{selected.nombreCompleto}</Text>
      <TouchableOpacity accessibilityRole="button" disabled={busy} onPress={() => setSelected(null)} style={styles.button}>
        <Text style={styles.link}>{t.changePlayer}</Text>
      </TouchableOpacity>
    </View> : fields.map(field => <ReplacementSearchField key={field.key}
      label={field.label} value={replacement[field.key]} phone={field.key === 'telefono'}
      busy={busy} search={search} errorText={pairCopy[language].searchError}
      onChange={value => setReplacement(current => ({ ...current, [field.key]: value }))}
      onSelect={setSelected} />)}
    <TouchableOpacity accessibilityRole="button" disabled={busy || !candidate}
      onPress={() => { if (candidate) onSubmit(candidate); }}
      style={[styles.button, (busy || !candidate) && styles.disabled]}>
      <Text style={styles.link}>{submitLabel ?? t.confirmReplacement}</Text>
    </TouchableOpacity>
  </View>;
}

const styles = StyleSheet.create({
  form: { gap: 6 },
  selection: { borderWidth: 1, borderColor: colors.line, borderRadius: 10, padding: 10, gap: 3 },
  text: { color: colors.ink, fontSize: 13, lineHeight: 20 },
  name: { color: colors.ink, fontSize: 16, fontWeight: '800' },
  button: { minHeight: 44, justifyContent: 'center' },
  link: { color: colors.green, fontSize: 13, fontWeight: '700' },
  disabled: { opacity: 0.55 },
});
