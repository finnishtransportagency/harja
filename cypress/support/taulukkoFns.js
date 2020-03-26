export function taulukonRiviTekstillaSync($taulukko, teksti) {
    console.log('---- KAIKKI ---');
    console.log($taulukko.find('.grid-taulukko').filter(':contains(' + teksti + ')'));
    return $taulukko.find('.grid-taulukko').filter(':contains(' + teksti + ')').last();
}

export function taulukonOsaTeksitllaSync($taulukko, teksti, indexLopusta) {
    let i = indexLopusta;
    if ((indexLopusta === undefined) || (indexLopusta === null)) {
        i = 0;
    }
    let $loydetytOsat = $taulukko.find('.grid-taulukko').filter(':contains(' + teksti + ')');
    return $loydetytOsat.eq($loydetytOsat.length-1+i);
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