export function kuluvaHoitovuosi(offset = 0) {
    let pvm = new Date();
    let vuosi = (pvm.getMonth() >= 9 ? pvm.getFullYear() : pvm.getFullYear() + 1) + offset;
    return `01.10.${vuosi}-30.09.${vuosi + 1}`;
}