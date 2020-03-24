export function taulukonRiviTekstillaSync($taulukko, teksti) {
    return $taulukko.find('.grid-taulukko').filter(':contains(' + teksti + ')').last();
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