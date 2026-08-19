export function localISODate(value=new Date()){const d=value instanceof Date?value:new Date(value);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
export const localToday=()=>localISODate(new Date())
export function localShiftDays(days,base=new Date()){const d=new Date(base);d.setHours(12,0,0,0);d.setDate(d.getDate()+days);return localISODate(d)}
