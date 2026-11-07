import React, { useEffect, useState } from 'react';
import { StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { parsePartnerCandidates } from './pairBooking';
import type { PartnerCandidate } from './pairBooking';
import { colors } from './theme';

export default function ReplacementSearchField({ label, value, phone, busy, search, onChange, onSelect, errorText }: {
  label: string; value: string; phone: boolean; busy: boolean; search: (query: string) => Promise<unknown>;
  onChange: (value: string) => void; onSelect: (candidate: PartnerCandidate) => void; errorText: string;
}) {
  const [results, setResults] = useState<{ query: string; matches: PartnerCandidate[]; failed: boolean } | null>(null);
  const query = value.trim();
  useEffect(() => {
    if (phone ? query.replace(/[^0-9]/g, '').length < 4 : query.length < 2) return;
    let active = true;
    const timer = setTimeout(async () => {
      try {
        const matches = parsePartnerCandidates(await search(query));
        if (active) setResults({ query, matches, failed: false });
      } catch {
        if (active) setResults({ query, matches: [], failed: true });
      }
    }, 350);
    return () => { active = false; clearTimeout(timer); };
  }, [query, phone, search]);

  const current = results?.query === query ? results : null;
  return <View>
    <Text style={styles.label}>{label}</Text>
    <TextInput accessibilityLabel={label} value={value} editable={!busy}
      keyboardType={phone ? 'phone-pad' : 'default'} maxLength={phone ? 20 : 100}
      onChangeText={onChange} style={styles.input} />
    {current?.failed && <Text accessibilityRole="alert" style={styles.error}>{errorText}</Text>}
    {current?.matches.map(candidate => (
      <TouchableOpacity key={candidate.id} accessibilityRole="button" disabled={busy}
        onPress={() => onSelect(candidate)} style={styles.suggestion}>
        <Text style={styles.name}>{candidate.nombreCompleto}</Text>
      </TouchableOpacity>
    ))}
  </View>;
}

const styles = StyleSheet.create({
  label: { color: colors.ink, fontSize: 13, fontWeight: '600', marginTop: 6, marginBottom: 4 },
  input: { minHeight: 44, borderWidth: 1, borderColor: colors.line, borderRadius: 8, paddingHorizontal: 10, color: colors.ink, fontSize: 16 },
  suggestion: { minHeight: 44, justifyContent: 'center', paddingHorizontal: 10, borderBottomWidth: 1, borderColor: colors.line },
  name: { color: colors.green, fontSize: 14, fontWeight: '600' },
  error: { color: '#A13F31', fontSize: 13, marginTop: 6 },
});
