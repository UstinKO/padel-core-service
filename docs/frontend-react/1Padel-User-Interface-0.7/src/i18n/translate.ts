import { messages } from './messages.ts';
import type { Locale } from './store.ts';
const normalize = (text:string) => text.replace(/\s+/g,' ').trim();
const escape = (text:string) => text.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
const templates = Object.entries(messages).filter(([key]) => /\{\d+\}/.test(key)).map(([key,value]) => {
  const indices:number[] = [];
  const parts = key.split(/(\{\d+\})/).map(part => /^\{\d+\}$/.test(part) ? (indices.push(Number(part.slice(1,-1))),'(.+?)') : escape(part));
  return {regex:new RegExp('^'+parts.join('')+'$'),indices,value,weight:key.replace(/\{\d+\}/g,'').length};
}).sort((a,b) => b.weight-a.weight);
/** Presentation text only: preserve names, values, identifiers and surrounding JSX spaces. */
export function translate(text:string, locale:Locale, depth=0):string {
  if (locale==='ru' || !text.trim() || depth>3) return text;
  const key=normalize(text);
  const wrap=(result:string) => (text.match(/^\s*/)?.[0]||'')+result+(text.match(/\s*$/)?.[0]||'');
  if(messages[key]) return wrap(messages[key][locale]);
  for(const template of templates) {
    const match=key.match(template.regex);
    if(match) {
      const values:Record<number,string>={};
      template.indices.forEach((index,n) => values[index]=translate(match[n+1],locale,depth+1));
      return wrap(template.value[locale].replace(/\{(\d+)\}/g,(_,index)=>values[Number(index)]??''));
    }
  }
  if(key.includes(' · ')) return wrap(key.split(' · ').map(part=>translate(part,locale,depth+1)).join(' · '));
  return text;
}
