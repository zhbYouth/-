// 把网页文件收集到 www/，供 Capacitor 打包和 GitHub Pages 发布使用。
import { cpSync, mkdirSync, rmSync } from 'node:fs';

rmSync('www', { recursive: true, force: true });
mkdirSync('www', { recursive: true });
for (const f of ['index.html', 'manifest.webmanifest', 'sw.js']) cpSync(f, `www/${f}`);
cpSync('icons', 'www/icons', { recursive: true });
cpSync('fonts', 'www/fonts', { recursive: true });
cpSync('textures', 'www/textures', { recursive: true });
console.log('www/ ready');
