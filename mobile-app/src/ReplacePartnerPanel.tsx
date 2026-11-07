import React, { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useAuth } from './AuthContext';
import type { ReplacementCandidate } from './auth';
import PairReplacementForm from './PairReplacementForm';
import { replacePartnerCopy } from './pairTerms';
import type { Language } from './schedule';
import { colors } from './theme';

export default function ReplacePartnerPanel({ id, partnerId, partnerName, language, onClose, onChanged }: {
  id: number; partnerId: number; partnerName: string; language: Language;
  onClose: () => void; onChanged: () => void;
}) {
  const { client } = useAuth();
  const t = replacePartnerCopy[language];
  const search = useCallback((query: string) => client.searchPartners(id, query), [client, id]);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const [complete, setComplete] = useState(false);
  const mounted = useRef(false);
  const running = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  async function submit(candidate: ReplacementCandidate) {
    if (running.current) return;
    running.current = true;
    setBusy(true);
    setFailed(false);
    try {
      await client.replacePartner(id, partnerId, candidate);
      if (mounted.current) setComplete(true);
    } catch {
      if (mounted.current) setFailed(true);
    } finally {
      running.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  const back = complete || failed ? onChanged : onClose;
  return <View style={styles.panel}>
    <Text accessibilityRole="header" style={styles.title}>{complete ? t.complete : t.title}</Text>
    {!complete && <>
      {!!partnerName && <Text style={styles.text}>{t.currentPartner}: {partnerName}</Text>}
      <Text style={styles.text}>{t.help}</Text>
      {failed
        ? <Text accessibilityRole="alert" style={styles.text}>{t.failed}</Text>
        : <PairReplacementForm language={language} busy={busy} search={search} onSubmit={candidate => { void submit(candidate); }}
            submitLabel={t.submit} />}
    </>}
    <TouchableOpacity accessibilityRole="button" disabled={busy} onPress={back} style={styles.button}>
      <Text style={styles.link}>{failed ? t.refresh : t.close}</Text>
    </TouchableOpacity>
  </View>;
}

const styles = StyleSheet.create({
  panel: { borderTopWidth: 1, borderColor: colors.line, paddingTop: 12, marginTop: 10, gap: 6 },
  title: { color: colors.ink, fontSize: 16, fontWeight: '800' },
  text: { color: colors.ink, fontSize: 13, lineHeight: 20 },
  button: { minHeight: 44, justifyContent: 'center' },
  link: { color: colors.green, fontSize: 13, fontWeight: '700' },
});
