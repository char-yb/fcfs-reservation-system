function hex(byte) {
    return byte.toString(16).padStart(2, '0');
}

export function uuidv4() {
    const bytes = Array.from({ length: 16 }, () => Math.floor(Math.random() * 256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;

    return [
        bytes.slice(0, 4).map(hex).join(''),
        bytes.slice(4, 6).map(hex).join(''),
        bytes.slice(6, 8).map(hex).join(''),
        bytes.slice(8, 10).map(hex).join(''),
        bytes.slice(10, 16).map(hex).join(''),
    ].join('-');
}

