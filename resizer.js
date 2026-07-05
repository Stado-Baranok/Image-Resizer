// resizer.js
#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const sharp = require('sharp');

const args = process.argv.slice(2);
let input = null, output = null;
let width = 0, height = 0, percent = 0, quality = 90, recursive = false, verbose = false;
let mode = 'fit', format = '';

for (let i = 0; i < args.length; i++) {
    if (args[i] === '-W' && i+1 < args.length) width = parseInt(args[++i], 10);
    else if (args[i] === '-H' && i+1 < args.length) height = parseInt(args[++i], 10);
    else if (args[i] === '-p' && i+1 < args.length) percent = parseInt(args[++i], 10);
    else if (args[i] === '-m' && i+1 < args.length) mode = args[++i];
    else if (args[i] === '-q' && i+1 < args.length) quality = parseInt(args[++i], 10);
    else if (args[i] === '-r') recursive = true;
    else if (args[i] === '-f' && i+1 < args.length) format = args[++i];
    else if (args[i] === '-v') verbose = true;
    else if (args[i] === '-h' || args[i] === '--help') {
        console.log(`Usage: node resizer.js <input> [output] [options]
  -W <N>     Width
  -H <N>     Height
  -p <N>     Percent
  -m <mode>  fit|fill|stretch
  -q <N>     Quality
  -r         Recursive
  -f <ext>   Output format
  -v         Verbose`);
        process.exit(0);
    } else if (!input) input = args[i];
    else if (!output) output = args[i];
}

if (!input) { console.error('Укажите входной файл или папку.'); process.exit(1); }
if (width === 0 && height === 0 && percent === 0) {
    console.error('Укажите ширину/высоту или процент.');
    process.exit(1);
}
if (!output) {
    const stat = fs.statSync(input);
    if (stat.isDirectory()) output = input;
    else {
        const ext = format || path.extname(input);
        output = path.join(path.dirname(input), path.basename(input, path.extname(input)) + '_resized' + (ext.startsWith('.') ? ext : '.' + ext));
    }
}

const exts = ['.png', '.jpg', '.jpeg', '.bmp', '.tiff', '.webp'];
function getFiles(dir) {
    let results = [];
    if (fs.statSync(dir).isFile()) {
        if (exts.includes(path.extname(dir).toLowerCase())) results.push(dir);
        return results;
    }
    const list = fs.readdirSync(dir);
    for (const f of list) {
        const full = path.join(dir, f);
        const stat = fs.statSync(full);
        if (stat.isDirectory() && recursive) {
            results = results.concat(getFiles(full));
        } else if (stat.isFile() && exts.includes(path.extname(f).toLowerCase())) {
            results.push(full);
        }
    }
    return results;
}

async function processImage(inputFile, outputFile) {
    let pipeline = sharp(inputFile);
    if (percent > 0) {
        pipeline = pipeline.resize(Math.round(percent));
    } else {
        const meta = await pipeline.metadata();
        let resizeOpts = {};
        if (mode === 'fit') {
            resizeOpts = { width, height, fit: 'inside' };
        } else if (mode === 'fill') {
            resizeOpts = { width, height, fit: 'cover', position: 'centre' };
        } else if (mode === 'stretch') {
            resizeOpts = { width, height, fit: 'fill' };
        }
        pipeline = pipeline.resize(resizeOpts);
    }
    const outExt = path.extname(outputFile).toLowerCase().slice(1) || format || 'jpg';
    if (outExt === 'jpg' || outExt === 'jpeg') {
        pipeline = pipeline.jpeg({ quality });
    } else if (outExt === 'webp') {
        pipeline = pipeline.webp({ quality });
    } else if (outExt === 'png') {
        pipeline = pipeline.png();
    } else {
        pipeline = pipeline.toFormat(outExt, { quality });
    }
    await pipeline.toFile(outputFile);
}

async function main() {
    const files = getFiles(input);
    if (files.length === 0) { console.log('Нет изображений.'); return; }
    const total = files.length;
    let processed = 0;
    for (const f of files) {
        let outFile = output;
        if (fs.statSync(output).isDirectory()) {
            const rel = path.relative(input, f);
            const outDir = path.join(output, path.dirname(rel));
            fs.mkdirSync(outDir, { recursive: true });
            const base = path.basename(f, path.extname(f));
            const ext = format || path.extname(f);
            outFile = path.join(outDir, base + (ext.startsWith('.') ? ext : '.' + ext));
        }
        try {
            await processImage(f, outFile);
            processed++;
            if (verbose) console.log(`✅ ${f} -> ${outFile}`);
            else {
                const pct = Math.floor(processed / total * 100);
                const bar = '█'.repeat(Math.floor(pct/2)) + '░'.repeat(50 - Math.floor(pct/2));
                process.stdout.write(`\r[${bar}] ${pct}% ${processed}/${total}`);
            }
        } catch (err) {
            console.error(`\n❌ Ошибка ${f}: ${err.message}`);
        }
    }
    if (!verbose) console.log();
    console.log(`✅ Обработано ${processed} файлов.`);
}

main().catch(console.error);
