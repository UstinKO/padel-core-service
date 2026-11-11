import type { LiveRound } from '../api/live';
import { sideNames } from '../api/live';

export async function exportBracket(
  rounds: LiveRound[],
  title: string,
  roundTitle: (round: LiveRound) => string,
  nextLabel: (id: number) => string,
) {
  await document.fonts.ready;
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  if (!context) throw new Error('Canvas unavailable');
  const width = 340,
    gap = 24,
    line = 22;
  context.font = '16px Manrope, Arial, sans-serif';
  const wrap = (text: string) => {
    const lines: string[] = [];
    let current = '';
    for (const char of text) {
      if (context.measureText(current + char).width > width - 40) {
        lines.push(current);
        current = char;
      } else current += char;
    }
    lines.push(current);
    return lines;
  };
  const columns = rounds.map((round) => ({
    title: wrap(roundTitle(round)),
    matches: round.matches.map((match) => ({
      lines: [
        ...wrap(sideNames(match.first) || '—'),
        `${match.first.games ?? match.first.score ?? '—'} : ${match.second.games ?? match.second.score ?? '—'}`,
        ...wrap(sideNames(match.second) || '—'),
        ...(match.nextMatchId == null ? [] : wrap(nextLabel(match.nextMatchId))),
      ],
    })),
  }));
  const header = wrap(title);
  const top = 40 + header.length * line;
  const height = Math.max(
    200,
    ...columns.map(
      (column) =>
        top +
        column.title.length * line +
        column.matches.reduce((size, match) => size + match.lines.length * line + 40, 0),
    ),
  );
  canvas.width = columns.length * (width + gap) + gap;
  canvas.height = height + gap;
  if (canvas.width > 16000 || canvas.height > 16000) throw new Error('Canvas too large');
  context.fillStyle = '#f5f5ef';
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.font = '16px Manrope, Arial, sans-serif';
  context.fillStyle = '#172e28';
  header.forEach((text, index) => context.fillText(text, gap, 30 + index * line));
  columns.forEach((column, index) => {
    const x = gap + index * (width + gap);
    let y = top;
    column.title.forEach((text) => {
      context.fillText(text, x, y);
      y += line;
    });
    y += 16;
    column.matches.forEach((match) => {
      const height = match.lines.length * line + 24;
      context.fillStyle = '#ffffff';
      context.fillRect(x, y, width, height);
      context.strokeStyle = '#dce2d8';
      context.strokeRect(x, y, width, height);
      context.fillStyle = '#172e28';
      match.lines.forEach((text, index) => context.fillText(text, x + 16, y + 24 + index * line));
      y += height + 16;
    });
  });
  const blob = await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('PNG unavailable'))),
      'image/png',
    ),
  );
  const url = URL.createObjectURL(blob);
  try {
    const link = document.createElement('a');
    link.href = url;
    link.download = '1padel-playoff.png';
    link.click();
  } finally {
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
