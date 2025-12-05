export const ladataanHarjaaTimeout = 30000;
export const clickTimeout = 4000;


export function kuluvaHoitokausiAlkuvuosi(offset = 0) {
    let pvm = new Date();
    return (pvm.getMonth() >= 9 ? pvm.getFullYear() : pvm.getFullYear() - 1) + offset;
}


export function avaaHarjaTimeoutilla() {
    // Varmista, että pääsivu on ladattu ennen testien aloitusta
    cy.visit("/");
    cy.get('.ladataan-harjaa', {timeout: ladataanHarjaaTimeout}).should('not.exist')
}
