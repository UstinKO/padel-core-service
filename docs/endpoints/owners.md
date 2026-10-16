# API: Владельцы (организаторы) — `/api/owners`

**Контроллер:** `OwnerController`  
**Назначение:** Просмотр и обновление данных администраторов/организаторов.

---

## Модель данных `OwnerDto`

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | Long | ID |
| `email` | String | Email |
| `nombreCompleto` | String | Полное имя |
| `role` | OwnerRole | SUPER_ADMIN / ORGANIZER |

---

## GET `/api/owners/super-admin`

**Аутентификация:** `ROLE_SUPER_ADMIN` или `ROLE_ORGANIZER`.

Возвращает данные первого найденного суперадмина в системе.

**Response 200 OK**: `OwnerDto`  
**Response 404 Not Found**: суперадмин не найден

### Замечания (ревью)

> **[MINOR]** Предполагается что суперадмин один. Если их несколько — возвращается первый из БД без гарантий порядка. Уточнить логику в `OwnerService.getSuperAdmin()`.

---

## GET `/api/owners/{id}` — Владелец по ID

**Аутентификация:** `ROLE_SUPER_ADMIN` или `ROLE_ORGANIZER`.

**Path param:** `id` — Long  
**Response 200 OK**: `OwnerDto`  
**Response 404 Not Found**

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `ORGANIZER` видит только собственный профиль (`OwnerService.getOwnerById`). Исходное замечание: `ROLE_ORGANIZER` может видеть данные любого другого `Owner` по ID, включая `SUPER_ADMIN`. Нужна проверка: `ORGANIZER` должен видеть только собственный профиль.

---

## PUT `/api/owners/{id}` — Обновить владельца

**Аутентификация:** `ROLE_SUPER_ADMIN` или `ROLE_ORGANIZER`.

**Request body** `application/json`: `OwnerDto`

**Response 200 OK**: обновлённый `OwnerDto`  
**Response 404 Not Found**: при `RuntimeException`

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `ORGANIZER` правит только свой профиль, роль через этот эндпоинт не меняется (`OwnerService.updateOwner`). Исходное замечание: `ROLE_ORGANIZER` может обновить данные любого другого `Owner`, включая смену роли на `SUPER_ADMIN`. Критичная дыра. Нужно: `ORGANIZER` может обновлять только `id == currentUser.id`, `SUPER_ADMIN` — любого.

> **[BUG]** `OwnerDto` принимается без `@Valid`, то есть bean validation не применяется.
