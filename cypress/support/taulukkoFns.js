export function taulukonRiviTekstillaSync($taulukko, teksti) {
    console.log('---- KAIKKI ---');
    console.log($taulukko.find('.grid-taulukko').filter(':contains(' + teksti + ')'));
    return $taulukko.find('.grid-taulukko').filter(':contains(' + teksti + ')').last();
}

export function taulukonOsaTeksitllaSync($taulukko, teksti, index) {
    let i = index;
    if ((data === undefined) || (data === null)) {
        i = $taulukko.length;
    }
    return $taulukko.find('.grid-taulukko').filter(':contains(' + teksti + ')').eq(i);
}

export function taulukonOsatSync($taulukko) {
    return $taulukko.children().eq(0).children();
}

export function taulukonOsaSync($taulukko, index) {
    return taulukonOsatSync($taulukko).eq(index);
}

export function rivinSarakeSync($rivi, index) {
    return taulukonOsaSync($rivi, index);
}