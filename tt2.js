const fs = require('fs');
const data = JSON.parse(fs.readFileSync('latest_scans.json', 'utf8'));
data.items.forEach(item => {
    const spec = item.prediction?.species || 'Unknown';
    if (spec === 'Dedenne') {
        fs.writeFileSync('dedenne_log.txt', item.payload?.debug?.rawOcrText.split('|').join('\n'));
    }
});
