const fs = require('fs');
const data = JSON.parse(fs.readFileSync('latest_scans.json', 'utf8'));
data.items.forEach(item => {
    const spec = item.prediction?.species || 'Unknown';
    console.log(spec + ' -------------');
    const raw = item.payload?.debug?.rawOcrText || '';
    raw.split('|').forEach(p => {
        if (p.startsWith('ClassifierSpriteKey') || p.startsWith('VariantClassifierConfidence') || p.startsWith('FullVariant')) {
            console.log(p);
        }
    });
});
