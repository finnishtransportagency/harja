/// <reference types="cypress" />

declare namespace Cypress {
    interface Chainable<Subject> {
        terminaaliKomento(): Chainable<string>

        taulukonRiviTekstilla(teksti: string): Chainable<any>

        rivinSarake(index: number): Chainable<any>

        taulukonOsa(index: number): Chainable<any>

        taulukonOsaPolussa(polku: Array<number>, debug?: boolean): Chainable<any>

        testaaOtsikot(otsikoidenArvot: Array<any>): Chainable<any>

        testaaRivienArvot(polkuTaulukkoon: Array<number>,
                          polkuSarakkeeseen: Array<number>,
                          arvot: Array<any>,
                          debug?: boolean): Chainable<any>

        gridOtsikot(): Chainable<{ grid: any; otsikot: { [key: string]: number }; }>

        pvmValitse(parametrit: { pvm: string }): Chainable<any>

        pvmTyhjenna(): Chainable<any>

        POTTestienAlustus(kohde: { [key: string]: number }, alikohde: { [key: string]: number }): Chainable<any>
    }
}