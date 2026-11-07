import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { offerSections } from './pairTerms';
import type { PairOffer } from './pairTerms';
import type { Language } from './schedule';
import { colors } from './theme';

export default function PairTermsSummary({ offer, language }: { offer: PairOffer; language: Language }) {
  const { event, sections } = offerSections(offer, language);
  return <View>
    {event.map(line => <Text key={line} style={styles.text}>{line}</Text>)}
    {sections.map(section => <View key={section.title} style={styles.section}>
      <Text accessibilityRole="header" style={styles.heading}>{section.title}</Text>
      {section.lines.map(line => <Text key={line} style={styles.text}>{line}</Text>)}
    </View>)}
  </View>;
}

const styles = StyleSheet.create({
  section: { borderTopWidth: 1, borderColor: colors.line, paddingTop: 10, marginTop: 10 },
  heading: { fontSize: 14, fontWeight: '700', color: colors.ink, marginBottom: 4 },
  text: { fontSize: 13, lineHeight: 20, color: colors.ink },
});
