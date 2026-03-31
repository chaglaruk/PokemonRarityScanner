const fs = require('fs');
const data = JSON.parse(fs.readFileSync('latest_scans_v2.json', 'utf8'));
console.log('Total items fetched:', data.items.length);
data.items.slice(0, 4).forEach((item, idx) => {
    const payload = item.payload; // It's already an object in latest_scans_v2.json
    const pred = payload.prediction;
    const debug = payload.debug;
    const raw = debug.rawOcrText || '';
    
    console.log(`\nScan #${idx + 1} -----------------------------------`);
    console.log(`Time: ${new Date(payload.uploadedAtEpochMs).toLocaleTimeString()}`);
    console.log(`Predicted Species: ${pred.species}`);
    console.log(`Predicted Shiny: ${pred.isShiny}`);
    console.log(`Predicted Costume: ${pred.hasCostume}`);
    
    const fields = {};
    raw.split('|').forEach(p => {
        const [k, v] = p.split(':');
        if (k) fields[k] = v;
    });
    
    console.log(`FullVariantSprite: ${fields.FullVariantSpriteKey || 'N/A'}`);
    console.log(`FullVariantShiny: ${fields.FullVariantShiny || 'N/A'}`);
    console.log(`FullVariantCostume: ${fields.FullVariantCostume || 'N/A'}`);
    console.log(`FullVariantDebug: ${fields.FullVariantDebug || 'N/A'}`);
    console.log(`ClassifierConfidence: ${fields.ClassifierConfidence || 'N/A'}`);
    console.log(`VariantClassifierConfidence: ${fields.VariantClassifierConfidence || 'N/A'}`);
    console.log(`ExplanationMode: ${fields.FullVariantExplanationMode || 'N/A'}`);
});
