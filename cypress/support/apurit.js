export function kuluvaHoitokausiAlkuvuosi(offset = 0) {
    let pvm = new Date();
    return (pvm.getMonth() >= 9 ? pvm.getFullYear() : pvm.getFullYear() - 1) + offset;
}
