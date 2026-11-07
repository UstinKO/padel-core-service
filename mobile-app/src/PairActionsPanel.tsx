import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Linking, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import type { Language } from './schedule';
import { useAuth } from './AuthContext';
import { colors } from './theme';
import PairTermsSummary from './PairTermsSummary';
import PairReplacementForm from './PairReplacementForm';
import { cancellationLines, pairExitCopy, pairTermsCopy, parseBookingTerms, parsePairCancellation } from './pairTerms';
import type { PairCancellation } from './pairTerms';

type Step = 'choices' | 'self' | 'full' | 'replace' | 'terms';

export default function PairActionsPanel({ id, language, inReserve, onClose, onChanged }: {
  id: number; language: Language; inReserve: boolean; onClose: () => void; onChanged: () => void;
}) {
  const { client } = useAuth();
  const t = pairTermsCopy[language];
  const exit = pairExitCopy[language];
  const [terms, setTerms] = useState<ReturnType<typeof parseBookingTerms> | null>(null);
  const [quote, setQuote] = useState<PairCancellation | null>(null);
  const [step, setStep] = useState<Step>('choices');
  const [result, setResult] = useState<PairCancellation | 'left' | 'replaced' | null>(null);
  const search = useCallback((query: string) => client.searchPartners(id, query), [client, id]);
  const [busy, setBusy] = useState(true);
  const [failed, setFailed] = useState(false);
  const [revision, setRevision] = useState(0);
  const running = useRef(false);
  const active = useRef(false);

  useEffect(() => {
    active.current = true;
    let mounted = true;
    setBusy(true);
    setFailed(false);
    Promise.all([client.myPairTerms(id), client.pairCancellation(id)]).then(([agreement, preview]) => {
      if (!mounted) return;
      setTerms(parseBookingTerms(agreement));
      setQuote(parsePairCancellation(preview));
    }).catch(() => { if (mounted) setFailed(true); }).finally(() => { if (mounted) setBusy(false); });
    return () => { mounted = false; active.current = false; };
  }, [client, id, revision]);

  async function run(mutate: () => Promise<void>) {
    if (running.current || busy) return;
    running.current = true;
    setBusy(true);
    setFailed(false);
    try {
      await mutate();
    } catch {
      if (active.current) {
        setFailed(true);
        setQuote(null);
        setStep('choices');
      }
    } finally {
      running.current = false;
      if (active.current) setBusy(false);
    }
  }

  const leaveHelp = inReserve ? exit.inReserve : t.leaveHelp;
  const button = (label: string, onPress: () => void, options: { help?: string; disabled?: boolean } = {}) => (
    <TouchableOpacity accessibilityRole="button" disabled={busy || options.disabled} onPress={onPress}
      style={[styles.button, options.help && styles.choice, (busy || options.disabled) && styles.disabled]}>
      <Text style={styles.link}>{label}</Text>
      {options.help && <Text style={styles.text}>{options.help}</Text>}
    </TouchableOpacity>
  );
  const paragraphs = (lines: string[]) => lines.map((line, i) => <Text key={i} style={styles.text}>{line}</Text>);

  if (result) {
    const message = result === 'left' ? (inReserve ? exit.leftInReserve : t.left)
      : result === 'replaced' ? exit.replaced : t.cancelled;
    return <View style={styles.panel}>
      <Text accessibilityRole="header" style={styles.title}>{message}</Text>
      {typeof result === 'object' && paragraphs(cancellationLines(result, language))}
      {button(t.back, onChanged)}
    </View>;
  }

  const heading = { choices: exit.choose, self: exit.self, full: exit.full, replace: exit.replaceTitle, terms: t.title }[step];

  return <View style={styles.panel}>
    <Text accessibilityRole="header" style={styles.title}>{heading}</Text>
    {busy && !quote && <View style={styles.loading}><ActivityIndicator color={colors.green} /><Text style={styles.text}>{exit.loading}</Text></View>}
    {quote && step === 'choices' && <>
      {(quote.canLeave || quote.canReplace) && button(exit.self, () => setStep('self'), { help: leaveHelp })}
      {button(exit.full, () => setStep('full'), { help: t.cancelHelp })}
      {button(t.title, () => setStep('terms'))}
    </>}
    {quote && step === 'self' && <>
      <Text style={styles.text}>{leaveHelp}</Text>
      {quote.canLeave && button(exit.confirmSelf, () => { void run(async () => {
        await client.leavePair(id);
        if (active.current) setResult('left');
      }); })}
      {quote.canReplace && button(exit.replace, () => setStep('replace'))}
    </>}
    {quote && step === 'replace' && <>
      <Text style={styles.text}>{exit.replaceHelp}</Text>
      <PairReplacementForm language={language} busy={busy} search={search} onSubmit={candidate => {
        void run(async () => {
          await client.replaceSelf(id, candidate);
          if (active.current) setResult('replaced');
        });
      }} />
    </>}
    {quote && step === 'full' && <>
      <Text style={styles.text}>{t.cancelHelp}</Text>
      {paragraphs(cancellationLines(quote, language))}
      {button(exit.confirmFull, () => { void run(async () => {
        const receipt = parsePairCancellation(await client.pairCancellation(id, quote.token));
        if (active.current) setResult(receipt);
      }); })}
    </>}
    {step === 'terms' && <>
      {terms?.terms ? <>
        <PairTermsSummary offer={terms.terms} language={language} />
        {terms.terms.regulationUrl && /^https?:\/\//i.test(terms.terms.regulationUrl)
          && button(t.rules, () => { void Linking.openURL(terms.terms!.regulationUrl!); })}
      </> : <Text style={styles.text}>{t.legacy}</Text>}
      {terms?.accepted && <Text style={styles.text}>{t.accepted}</Text>}
      {terms?.canAccept && terms.terms && !failed && button(t.accept, () => {
        const token = terms.terms!.token;
        void run(async () => {
          const next = parseBookingTerms(await client.myPairTerms(id, token));
          if (!active.current) return;
          setTerms(next);
          setQuote(null);
          setRevision(value => value + 1);
        });
      })}
    </>}
    {failed && <>
      <Text accessibilityRole="alert" style={styles.text}>{t.failed}</Text>
      {button(exit.retry, () => setRevision(value => value + 1))}
    </>}
    {step === 'choices' ? button(t.back, onClose)
      : button(exit.back, () => setStep(step === 'replace' ? 'self' : 'choices'))}
  </View>;
}

const styles = StyleSheet.create({
  panel: { borderTopWidth: 1, borderColor: colors.line, paddingTop: 12, marginTop: 10, gap: 6 },
  title: { color: colors.ink, fontSize: 16, fontWeight: '800' },
  text: { color: colors.ink, fontSize: 13, lineHeight: 20 },
  button: { minHeight: 44, justifyContent: 'center' },
  choice: { borderWidth: 1, borderColor: colors.line, borderRadius: 10, padding: 10, gap: 3 },
  disabled: { opacity: 0.55 },
  link: { color: colors.green, fontSize: 13, fontWeight: '700' },
  loading: { flexDirection: 'row', gap: 8, alignItems: 'center' },
});
