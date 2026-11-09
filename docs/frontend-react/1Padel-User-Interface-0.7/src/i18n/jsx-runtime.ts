// Translate only rendered text, never model values, API fields, user input or DOM nodes.
// React owns the entire tree; language updates preserve component identity and form state.
import { createElement, Children, Fragment, type ElementType, type ReactNode } from 'react';
import { jsx as baseJsx, jsxs as baseJsxs } from 'react/jsx-runtime';
import { useLocale } from './react';
import { translate } from './translate';
export { Fragment };
export type { JSX } from 'react/jsx-runtime';
type Props = Record<string, any>;
function LocalizedText({text}: {text: string}) { return translate(text,useLocale()); }
function textChildren(children: any, locale: ReturnType<typeof useLocale>): any {
  return typeof children === 'string' ? translate(children,locale) : Array.isArray(children) ? Children.toArray(children).map(child => textChildren(child,locale)) : children;
}
function LocalizedHost({tag, original}: {tag: ElementType; original: Props}) {
  const locale = useLocale();
  const props = {...original};
  for (const name of ['aria-label','aria-description','title','placeholder','alt']) if (typeof props[name] === 'string') props[name] = translate(props[name],locale);
  if (tag === 'option' && props.value === undefined && typeof props.children === 'string') props.value = props.children;
  // textarea content and form values are user data, not translation keys.
  if (tag !== 'textarea' && tag !== 'script' && tag !== 'style' && props.translate !== 'no') props.children = textChildren(props.children,locale);
  return Array.isArray(props.children) ? createElement(tag,props,...props.children) : createElement(tag,props);
}
function LocalizedComponent({component, original}: {component:ElementType; original:Props}) {
  const locale=useLocale(); const props={...original};
  for(const name of ['aria-label','aria-description','title','placeholder','alt']) if(typeof props[name]==='string') props[name]=translate(props[name],locale);
  return Array.isArray(props.children) ? createElement(component,props,...props.children) : createElement(component,props);
}
function localizeChildren(children: any): any {
  return typeof children === 'string' ? baseJsx(LocalizedText,{text:children}) : Array.isArray(children) ? Children.toArray(children).map((child,index) => typeof child === 'string' ? baseJsx(LocalizedText,{text:child},index) : child) : children;
}
export function jsx(type: ElementType | typeof Fragment, props: Props, key?: string): ReactNode {
  if (typeof type === 'string') return baseJsx(LocalizedHost,{tag:type,original:props},key);
  const original={...props,children:localizeChildren(props.children)};
  if (['aria-label','aria-description','title','placeholder','alt'].some(name=>typeof props[name]==='string')) return baseJsx(LocalizedComponent,{component:type,original},key);
  return (Array.isArray(original.children)?baseJsxs:baseJsx)(type as ElementType,original,key);
}
export const jsxs = jsx;
