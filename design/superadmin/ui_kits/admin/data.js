const NAMES = ['Dilnoza Karimova', 'Bekzod Tursunov', 'Madina Yusupova', 'Javohir Aliyev', 'Shahlo Nazarova', 'Otabek Ergashev', 'Nilufar Saidova', 'Sardor Qodirov', 'Gulnora Ismoilova', 'Rustam Xolmatov', 'Kamola Abdullayeva', 'Jasur Mirzayev', 'Feruza Rasulova', 'Ulug‘bek Hamidov', 'Zarina To‘xtayeva', 'Akmal Nurmatov', 'Sevara Qosimova', 'Doniyor Sharipov', 'Malika Ortiqova', 'Shoxrux G‘aniyev'];
const STATUSES = ['Faol', 'Faol', 'Ro‘yxatdan o‘tmoqda', 'Bloklangan', 'Faol', 'O‘chirish so‘ralgan', 'Faol', 'Yopilgan', 'Faol', 'Faol', 'Ro‘yxatdan o‘tmoqda', 'Faol', 'Faol', 'Faol', 'Bloklangan', 'Faol', 'Faol', 'Ro‘yxatdan o‘tmoqda', 'Faol', 'Faol'];
const CH = ['uzum', 'wb', 'instagram', 'ozon', 'telegram', 'uzum', 'offline', 'other', 'wb', 'uzum', 'instagram', 'ozon', 'uzum', 'telegram', 'wb', 'uzum', 'instagram', 'offline', 'uzum', 'wb'];
const pad = (n) => String(n).padStart(2, '0');

export const SELLERS = NAMES.map((name, i) => ({
  id: 'slr_' + (1040 + i),
  name,
  tg: String(482019377 + i * 7310291).slice(0, 9),
  username: '@' + name.split(' ')[0].toLowerCase().replace(/[‘’]/g, '') + '_shop',
  status: STATUSES[i],
  channel: CH[i],
  channels: [CH[i], CH[(i + 3) % CH.length]].filter((v, j, a) => a.indexOf(v) === j),
  calcs: [214, 96, 3, 41, 180, 12, 77, 0, 133, 58, 1, 92, 66, 140, 8, 201, 49, 2, 115, 87][i],
  products: [38, 14, 1, 9, 27, 4, 11, 0, 22, 8, 0, 17, 10, 19, 3, 31, 7, 0, 16, 12][i],
  registered: `${pad((i * 3) % 28 + 1)}.${pad((i % 9) + 2)}.2026`,
  last: `${pad(5 - (i % 5))}.11.2026 ${pad(8 + (i * 5) % 14)}:${pad((i * 17) % 60)}`,
  phone: '+998 9' + (i % 10) + ' *** ** ' + pad((i * 7) % 100),
  plan: 'Free (Beta)',
}));

export const ACCESS_REQUESTS = [
  { id: 'req_7f3a9c21', seller: 'Dilnoza Karimova', sellerId: 'slr_1040', admin: 'Aziz Rahimov', reason: 'Hisoblashdagi xatoni tekshirish', duration: 30, status: 'Ruxsat berilgan', created: '05.11.2026 14:28', expiresIn: 1421 },
  { id: 'req_5b21e004', seller: 'Bekzod Tursunov', sellerId: 'slr_1041', admin: 'Shahlo Nazarova', reason: 'Narx shabloni bo‘yicha murojaat', duration: 45, status: 'Kutilmoqda', created: '05.11.2026 13:55' },
  { id: 'req_44c0a7d9', seller: 'Madina Yusupova', sellerId: 'slr_1042', admin: 'Aziz Rahimov', reason: 'Ro‘yxatdan o‘tishdagi muammo', duration: 30, status: 'Kutilmoqda', created: '05.11.2026 12:10' },
  { id: 'req_9e81b3f2', seller: 'Javohir Aliyev', sellerId: 'slr_1043', admin: 'Shahlo Nazarova', reason: 'Foyda hisobi noto‘g‘ri chiqyapti', duration: 60, status: 'Rad etilgan', created: '04.11.2026 18:02' },
  { id: 'req_0d6e5a11', seller: 'Shahlo Nazarova', sellerId: 'slr_1044', admin: 'Aziz Rahimov', reason: 'Komissiya stavkasini tekshirish', duration: 30, status: 'Muddati tugagan', created: '04.11.2026 10:40' },
  { id: 'req_c2f8d7a0', seller: 'Otabek Ergashev', sellerId: 'slr_1045', admin: 'Shahlo Nazarova', reason: 'Eksport xatosi', duration: 30, status: 'Bekor qilingan', created: '03.11.2026 16:21' },
  { id: 'req_a1b9e6c3', seller: 'Gulnora Ismoilova', sellerId: 'slr_1048', admin: 'Aziz Rahimov', reason: 'Valyuta kursi bo‘yicha savol', duration: 45, status: 'Kutilmoqda', created: '05.11.2026 09:47' },
];

export const AUDIT = [
  { id: 1, at: '05.11.2026 14:31', actor: 'Aziz Rahimov', role: 'SUPERADMIN', action: 'Tijoriy ma’lumotni ko‘rdi', target: 'Dilnoza Karimova', meta: 'req_7f3a9c21 · Mahsulotlar' },
  { id: 2, at: '05.11.2026 14:28', actor: 'Dilnoza Karimova', role: 'Sotuvchi', action: 'Kirish ruxsatini berdi', target: 'Aziz Rahimov', meta: '30 daqiqa · faqat o‘qish' },
  { id: 3, at: '05.11.2026 14:27', actor: 'Aziz Rahimov', role: 'SUPERADMIN', action: 'Kirish so‘rovi yubordi', target: 'Dilnoza Karimova', meta: 'Sabab: Hisoblashdagi xatoni tekshirish' },
  { id: 4, at: '05.11.2026 11:02', actor: 'Aziz Rahimov', role: 'SUPERADMIN', action: 'Sotuvchini blokladi', target: 'Javohir Aliyev', meta: 'Sabab: Spam xabarlar yuborish' },
  { id: 5, at: '04.11.2026 19:15', actor: 'Shahlo Nazarova', role: 'SUPPORT', action: 'Tizimga kirdi', target: '—', meta: '2FA · 84.54.***.***' },
  { id: 6, at: '04.11.2026 17:40', actor: 'Aziz Rahimov', role: 'SUPERADMIN', action: '2FA’ni qayta o‘rnatdi', target: 'Kamron Usmonov', meta: 'Sabab: Telefon yo‘qolgan' },
];

export const ADMINS = [
  { id: 'adm_1', name: 'Aziz Rahimov', email: 'aziz@sellerflow.uz', role: 'SUPERADMIN', twofa: true, last: '05.11.2026 14:31', status: 'Faol' },
  { id: 'adm_2', name: 'Shahlo Nazarova', email: 'shahlo@sellerflow.uz', role: 'SUPPORT', twofa: true, last: '05.11.2026 13:55', status: 'Faol' },
  { id: 'adm_3', name: 'Kamron Usmonov', email: 'kamron@sellerflow.uz', role: 'SUPPORT', twofa: false, last: '02.11.2026 09:12', status: 'Faol' },
  { id: 'adm_4', name: 'Lola Yo‘ldosheva', email: 'lola@sellerflow.uz', role: 'SUPPORT', twofa: true, last: '28.10.2026 18:04', status: 'Bloklangan' },
];

/** Shown only during a consented READ-ONLY session. */
export const SELLER_PRODUCTS = [
  { id: 'p1', name: 'Bolalar ko‘ylagi, paxta', channel: 'uzum', cost: 62000, price: 129000, fee: 19350, profit: 38250, margin: 29.65 },
  { id: 'p2', name: 'Ayollar sumkasi, charm', channel: 'wb', cost: 185000, price: 349000, fee: 62820, profit: 68430, margin: 19.61 },
  { id: 'p3', name: 'Termos 750 ml', channel: 'uzum', cost: 48000, price: 89000, fee: 13350, profit: 13225, margin: 14.86 },
  { id: 'p4', name: 'Sport poyabzal, 42', channel: 'instagram', cost: 210000, price: 399000, fee: 0, profit: 159000, margin: 39.85 },
  { id: 'p5', name: 'Oshxona pichoqlari to‘plami', channel: 'ozon', cost: 96000, price: 159000, fee: 31800, profit: -2400, margin: -1.51 },
];

export const CHANNEL_COUNTS = [
  { id: 'uzum', n: 612 }, { id: 'wb', n: 341 }, { id: 'instagram', n: 288 }, { id: 'telegram', n: 196 }, { id: 'ozon', n: 154 }, { id: 'offline', n: 97 }, { id: 'other', n: 41 },
];
export const SIGNUPS = [42, 51, 38, 64, 58, 71, 66, 80, 74, 69, 88, 92, 79, 86];
