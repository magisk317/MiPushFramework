import os
import re

EXTENSIONS_IMPORTS = """import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW"""

def remove_logger_block(content):
    start_idx = content.find("private val logger = object {")
    if start_idx == -1: return content
    
    line_start = content.rfind("\n", 0, start_idx)
    if line_start != -1: start_idx = line_start + 1
        
    brace_count = 0
    in_block = False
    end_idx = -1
    
    first_brace = content.find("{", start_idx)
    if first_brace == -1: return content
    
    for i in range(first_brace, len(content)):
        if content[i] == '{':
            brace_count += 1
            in_block = True
        elif content[i] == '}':
            brace_count -= 1
            
        if in_block and brace_count == 0:
            end_idx = i
            break
            
    if end_idx != -1:
        if end_idx + 1 < len(content) and content[end_idx + 1] == '\n':
            end_idx += 1
        return content[:start_idx] + content[end_idx + 1:]
    return content

def process_file(filepath):
    if not filepath.endswith('.kt'): return
    with open(filepath, 'r') as f:
        content = f.read()
    
    if 'logger.' not in content and 'private val logger' not in content:
        return
        
    new_content = content.replace('logger.d(', 'logD(')
    new_content = new_content.replace('logger.i(', 'logI(')
    new_content = new_content.replace('logger.e(', 'logE(')
    new_content = new_content.replace('logger.w(', 'logW(')
    new_content = new_content.replace('logger.v(', 'logV(')
    
    while "private val logger = object {" in new_content:
        prev = new_content
        new_content = remove_logger_block(new_content)
        if new_content == prev: break
        
    if ('logD(' in new_content or 'logI(' in new_content or 'logE(' in new_content or 'logW(' in new_content or 'logV(' in new_content):
        if 'package io.github.magisk317.mipush.common.utils' not in new_content:
            if 'import io.github.magisk317.mipush.common.utils.logD' not in new_content:
                new_content = re.sub(r'(package [^\n]+\n)', r'\1\n' + EXTENSIONS_IMPORTS + '\n', new_content, count=1)
            
    with open(filepath, 'w') as f:
        f.write(new_content)

for root, _, files in os.walk('.'):
    for file in files:
        process_file(os.path.join(root, file))
