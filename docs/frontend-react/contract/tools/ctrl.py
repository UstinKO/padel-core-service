import re, sys, os, json

ROOT = sys.argv[1]
MAP_RE = re.compile(r'@(Get|Post|Put|Patch|Delete|Request)Mapping\s*(\((.*?)\))?\s*$', re.S)

def mapping_paths(args):
    if not args:
        return ['']
    m = re.findall(r'"([^"]*)"', args)
    return m or ['']

def split_methods(src):
    """Yield (annotations, signature, body) for each method with a *Mapping annotation."""
    lines = src.split('\n')
    i = 0
    n = len(lines)
    while i < n:
        line = lines[i].strip()
        if re.match(r'@(Get|Post|Put|Patch|Delete|Request)Mapping', line) and i > 0:
            # collect annotation block upward
            j = i
            while j > 0 and (lines[j-1].strip().startswith('@') or lines[j-1].strip().startswith('//') or lines[j-1].strip().endswith(',') or lines[j-1].strip().endswith('(')):
                j -= 1
            ann = []
            k = i
            # collect annotations downward until signature
            while k < n and not re.search(r'\b(public|private|protected)\b[^=]*\(', lines[k]):
                ann.append(lines[k].strip())
                k += 1
            if k >= n:
                break
            pre = [l.strip() for l in lines[j:i]]
            # class-level mapping detection: signature is 'class'
            sig_lines = []
            depth = 0
            while k < n:
                sig_lines.append(lines[k].strip())
                if '{' in lines[k]:
                    break
                k += 1
            sig = ' '.join(sig_lines)
            if ' class ' in sig:
                i = k + 1
                continue
            # body by brace matching
            body_lines = []
            depth = lines[k].count('{') - lines[k].count('}')
            k += 1
            while k < n and depth > 0:
                depth += lines[k].count('{') - lines[k].count('}')
                body_lines.append(lines[k])
                k += 1
            yield pre + ann, sig, '\n'.join(body_lines)
            i = k
        else:
            i += 1

out = []
for dp, _, fs in os.walk(ROOT):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(dp, f)
        src = open(p, encoding='utf-8').read()
        if not re.search(r'^@(Controller|RestController)', src, re.M):
            continue
        rest = bool(re.search(r'^@RestController', src, re.M))
        cm = re.search(r'^@RequestMapping\s*\((.*?)\)\s*$', src, re.M | re.S)
        base = mapping_paths(cm.group(1))[0] if cm else ''
        cls_pre = re.findall(r'^@PreAuthorize\("([^"]*)"\)\s*\n(?:@\w+.*\n)*public class', src, re.M)
        cdecl = re.search(r'^public (?:abstract )?class .*$', src, re.M)
        body_src = src[cdecl.end():] if cdecl else src
        for ann, sig, body in split_methods(body_src):
            annj = ' '.join(ann)
            mm = re.search(r'@(Get|Post|Put|Patch|Delete|Request)Mapping\s*(\((.*?)\))?(?=\s*(@|public|private|protected|$))', annj)
            if not mm:
                continue
            verb = mm.group(1).upper()
            if verb == 'REQUEST':
                vm = re.search(r'RequestMethod\.(\w+)', mm.group(3) or '')
                verb = vm.group(1) if vm else 'ANY'
            paths = mapping_paths(mm.group(3))
            pre = re.findall(r'@PreAuthorize\("([^"]*)"\)', annj)
            respbody = rest or '@ResponseBody' in annj or 'ResponseEntity' in sig
            name = re.search(r'(\w+)\s*\(', sig.split(' throws ')[0].split('(')[0] + '(')
            mname = re.search(r'\s(\w+)\s*\(', sig)
            params = []
            for pm in re.finditer(r'@(RequestParam|PathVariable|ModelAttribute|RequestBody|Valid\s+@ModelAttribute|RequestHeader|CookieValue)(\([^)]*\))?\s+(?:final\s+)?([\w<>,\s\[\]?]+?)\s+(\w+)\s*[,)]', sig):
                kind = pm.group(1).split()[-1].replace('@', '')
                args = pm.group(2) or ''
                req = 'required = false' not in args and 'required=false' not in args and 'defaultValue' not in args
                pname = re.search(r'(?:value|name)\s*=\s*"([^"]+)"', args) or re.search(r'^\(\s*"([^"]+)"', args)
                params.append(f"{kind}:{(pname.group(1) if pname else pm.group(4))}:{pm.group(3).strip()}{'' if req else '?'}")
            if 'Principal' in sig or 'Authentication' in sig or '@AuthenticationPrincipal' in sig:
                params.append('auth')
            if 'HttpServletRequest' in sig:
                params.append('HttpServletRequest')
            if 'HttpSession' in sig:
                params.append('HttpSession')
            model = sorted(set(re.findall(r'model\.addAttribute\(\s*"([^"]+)"', body) + re.findall(r'(?:mav|modelAndView)\.addObject\(\s*"([^"]+)"', body)))
            flash = sorted(set(re.findall(r'addFlashAttribute\(\s*"([^"]+)"', body)))
            rattr = sorted(set(re.findall(r'redirectAttributes\.addAttribute\(\s*"([^"]+)"', body)))
            rets = re.findall(r'return\s+"([^"]*)"', body) + re.findall(r'new ModelAndView\(\s*"([^"]*)"', body)
            retexpr = re.findall(r'return\s+("redirect:[^;]*);', body)
            views = sorted(set(r for r in rets if not r.startswith('redirect:') and not r.startswith('forward:')))
            redirects = sorted(set(r for r in rets if r.startswith(('redirect:', 'forward:'))) | set(e for e in retexpr if '+' in e))
            out.append(dict(file=os.path.relpath(p, ROOT), base=base, verb=verb, paths=[base + x for x in paths],
                            method=mname.group(1) if mname else '?', json=respbody, pre=pre or cls_pre,
                            params=params, model=model, flash=flash, rattr=rattr, views=views, redirects=redirects,
                            sig=sig[:200]))
json.dump(out, open(sys.argv[2], 'w'), ensure_ascii=False, indent=1)
print(len(out), 'methods')
