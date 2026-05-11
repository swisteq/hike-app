import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  createExpeditionFromGpx,
  updateExpeditionEquipment,
  createMultiDayExpedition,
  addDayTrail,
} from '../api/client';

const EQUIPMENT_ITEMS = [
  { value: 'RACZKI',                   label: 'Raczki' },
  { value: 'RAKI',                     label: 'Raki' },
  { value: 'CZEKAN',                   label: 'Czekan' },
  { value: 'KIJKI_TREKKINGOWE',        label: 'Kijki trekkingowe' },
  { value: 'STUPTUTY',                 label: 'Stuptuty' },
  { value: 'KASK',                     label: 'Kask' },
  { value: 'LATARKA_CZOLOWA',          label: 'Latarka czołowa' },
  { value: 'KREM_Z_FILTREM',           label: 'Krem z filtrem' },
  { value: 'OKULARY_PRZECIWSLONECZNE', label: 'Okulary przeciwsłoneczne' },
];

const JOIN_OPTIONS = [
  { value: 'AUTO',              label: 'Automatyczne',  desc: 'Każdy może dołączyć od razu' },
  { value: 'APPROVAL_REQUIRED', label: 'Za akceptacją', desc: 'Prośby wymagają Twojej zgody' },
];

function RadioGroup({ name, options, value, onChange }) {
  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
      {options.map(opt => (
        <label key={opt.value} className={`flex items-start gap-3 p-3 border rounded-lg cursor-pointer transition-colors ${
          value === opt.value ? 'border-mountain-400 bg-mountain-50' : 'border-gray-200 hover:border-gray-300'
        }`}>
          <input
            type="radio"
            name={name}
            value={opt.value}
            checked={value === opt.value}
            onChange={e => onChange(e.target.value)}
            className="mt-0.5 accent-mountain-600 shrink-0"
          />
          <div>
            <div className="text-sm font-medium text-gray-800">{opt.label}</div>
            <div className="text-xs text-gray-500 mt-0.5">{opt.desc}</div>
          </div>
        </label>
      ))}
    </div>
  );
}

function VisibilitySelector({ value, onChange }) {
  const isFriends = value === 'FRIENDS_ONLY' || value === 'FRIENDS_AND_GROUPS';
  const isGroups  = value === 'GROUPS_ONLY'  || value === 'FRIENDS_AND_GROUPS';

  const toggle = (type) => {
    if (type === 'friends') {
      const nf = !isFriends;
      if (nf && isGroups)  onChange('FRIENDS_AND_GROUPS');
      else if (nf)         onChange('FRIENDS_ONLY');
      else if (isGroups)   onChange('GROUPS_ONLY');
      else                 onChange('PUBLIC');
    } else {
      const ng = !isGroups;
      if (ng && isFriends) onChange('FRIENDS_AND_GROUPS');
      else if (ng)         onChange('GROUPS_ONLY');
      else if (isFriends)  onChange('FRIENDS_ONLY');
      else                 onChange('PUBLIC');
    }
  };

  const isPublic = value === 'PUBLIC';

  return (
    <div className="space-y-2">
      <label className={`flex items-start gap-3 p-3 border rounded-lg cursor-pointer transition-colors ${
        isPublic ? 'border-mountain-400 bg-mountain-50' : 'border-gray-200 hover:border-gray-300'
      }`}>
        <input type="radio" checked={isPublic} onChange={() => onChange('PUBLIC')}
          className="mt-0.5 accent-mountain-600 shrink-0" />
        <div>
          <div className="text-sm font-medium text-gray-800">Publiczna</div>
          <div className="text-xs text-gray-500 mt-0.5">Widoczna dla wszystkich użytkowników</div>
        </div>
      </label>
      <div className="grid grid-cols-2 gap-2">
        {[
          { key: 'friends', label: 'Znajomi', desc: 'Widoczna dla Twoich znajomych', checked: isFriends },
          { key: 'groups',  label: 'Wspólne grupy', desc: 'Widoczna dla członków Twoich grup', checked: isGroups },
        ].map(({ key, label, desc, checked }) => (
          <label key={key} className={`flex items-start gap-3 p-3 border rounded-lg cursor-pointer transition-colors ${
            checked ? 'border-mountain-400 bg-mountain-50' : 'border-gray-200 hover:border-gray-300'
          }`}>
            <input type="checkbox" checked={checked} onChange={() => toggle(key)}
              className="mt-0.5 accent-mountain-600 shrink-0" />
            <div>
              <div className="text-sm font-medium text-gray-800">{label}</div>
              <div className="text-xs text-gray-500 mt-0.5">{desc}</div>
            </div>
          </label>
        ))}
      </div>
    </div>
  );
}

function getDayList(startDate, endDate) {
  if (!startDate || !endDate) return [];
  const start = new Date(startDate);
  const end = new Date(endDate);
  if (end <= start) return [];
  const days = [];
  let current = new Date(start);
  let num = 1;
  while (current <= end) {
    days.push({
      dayNumber: num,
      dayDate: current.toISOString().split('T')[0],
      label: `Dzień ${num} — ${current.toLocaleDateString('pl-PL', { weekday: 'short', day: 'numeric', month: 'short' })}`,
    });
    current.setDate(current.getDate() + 1);
    num++;
  }
  return days;
}

export default function NewExpeditionPage() {
  const navigate = useNavigate();

  const [gpxFile, setGpxFile] = useState(null);
  const [form, setForm] = useState({
    name: '',
    plannedDate: '',
    endDate: '',
    startTime: '',
    description: '',
    joinMode: 'AUTO',
    visibility: 'PUBLIC',
  });
  const [dayFiles, setDayFiles] = useState({}); // dayNumber -> File
  const [dayTypes, setDayTypes] = useState({}); // dayNumber -> 'gpx' | 'rest'
  const [equipment, setEquipment] = useState({});
  const [equipmentOpen, setEquipmentOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const today = new Date().toISOString().split('T')[0];
  const dayList = getDayList(form.plannedDate, form.endDate);
  const isMultiDay = dayList.length > 0;

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file && !file.name.toLowerCase().endsWith('.gpx')) {
      setError('Plik musi mieć rozszerzenie .gpx');
      setGpxFile(null);
      return;
    }
    setError('');
    setGpxFile(file || null);
  };

  const handleDayFileChange = (dayNumber, e) => {
    const file = e.target.files[0];
    if (file && !file.name.toLowerCase().endsWith('.gpx')) {
      setError('Plik musi mieć rozszerzenie .gpx');
      return;
    }
    setError('');
    setDayFiles(prev => ({ ...prev, [dayNumber]: file || null }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!isMultiDay && !gpxFile) { setError('Wybierz plik GPX z trasą'); return; }
    if (!form.startTime) { setError('Podaj godzinę startu'); return; }
    setError('');
    setLoading(true);
    try {
      let expeditionId;

      if (isMultiDay) {
        const { data } = await createMultiDayExpedition({
          name: form.name,
          description: form.description || undefined,
          plannedDate: form.plannedDate,
          endDate: form.endDate,
          startTime: form.startTime,
          joinMode: form.joinMode,
          visibility: form.visibility,
        });
        expeditionId = data.id;

        for (const day of dayList) {
          const isRest = dayTypes[day.dayNumber] === 'rest';
          const file = isRest ? null : dayFiles[day.dayNumber];
          if (file) {
            const fd = new FormData();
            fd.append('file', file);
            await addDayTrail(expeditionId, day.dayNumber, fd);
          }
        }
      } else {
        const formData = new FormData();
        formData.append('file', gpxFile);
        formData.append('name', form.name);
        formData.append('plannedDate', form.plannedDate);
        formData.append('startTime', form.startTime);
        if (form.description) formData.append('description', form.description);
        formData.append('joinMode', form.joinMode);
        formData.append('visibility', form.visibility);
        const { data } = await createExpeditionFromGpx(formData);
        expeditionId = data.id;
      }

      const equipmentList = Object.entries(equipment)
        .filter(([, level]) => level)
        .map(([item, level]) => ({ item, level }));
      if (equipmentList.length > 0) {
        await updateExpeditionEquipment(expeditionId, equipmentList);
      }

      navigate(`/expeditions/${expeditionId}`);
    } catch (err) {
      setError(err.response?.data?.message || 'Błąd podczas tworzenia wyprawy');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <Link to="/expeditions" className="text-mountain-600 hover:text-mountain-800 text-sm mb-4 inline-block">
        ← Moje wyprawy
      </Link>

      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Nowa wyprawa</h1>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm mb-5">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Nazwa */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Nazwa wyprawy *</label>
            <input
              type="text" required value={form.name}
              onChange={e => setForm({ ...form, name: e.target.value })}
              className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
              placeholder="np. Wyprawa na Kasprowy Wierch"
            />
          </div>

          {/* Data i godzina startu */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Data startu *</label>
              <input
                type="date" required value={form.plannedDate}
                min={today}
                onChange={e => setForm({ ...form, plannedDate: e.target.value, endDate: '' })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Data końca <span className="text-gray-400 font-normal">(wielodniowa)</span></label>
              <input
                type="date" value={form.endDate}
                min={form.plannedDate || today}
                onChange={e => setForm({ ...form, endDate: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
              />
            </div>
          </div>

          <div className="w-1/2 pr-1.5">
            <label className="block text-sm font-medium text-gray-700 mb-1">Godzina startu *</label>
            <input
              type="time" required value={form.startTime}
              onChange={e => setForm({ ...form, startTime: e.target.value })}
              className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
            />
          </div>

          {/* GPX — jednodniowa lub wielodniowa */}
          {!isMultiDay ? (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Plik trasy GPX * <span className="text-gray-400 font-normal">(z mapa-turystyczna.pl)</span>
              </label>
              <label className="flex flex-col items-center justify-center w-full h-28 border-2 border-dashed border-gray-300 rounded-lg cursor-pointer bg-gray-50 hover:bg-gray-100 transition-colors">
                <div className="text-center">
                  {gpxFile ? (
                    <>
                      <div className="text-mountain-600 font-medium text-sm">{gpxFile.name}</div>
                      <div className="text-gray-400 text-xs mt-1">Kliknij, aby zmienić plik</div>
                    </>
                  ) : (
                    <>
                      <div className="text-gray-500 text-sm">Kliknij lub przeciągnij plik .gpx</div>
                      <div className="text-gray-400 text-xs mt-1">Obsługiwane: .gpx</div>
                    </>
                  )}
                </div>
                <input type="file" accept=".gpx" onChange={handleFileChange} className="hidden" />
              </label>
            </div>
          ) : (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Trasy GPX per dzień <span className="text-gray-400 font-normal">(opcjonalnie)</span>
              </label>
              <div className="space-y-2">
                {dayList.map(day => {
                  const isRest = dayTypes[day.dayNumber] === 'rest';
                  return (
                    <div key={day.dayNumber} className="p-3 border border-gray-200 rounded-lg bg-gray-50">
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-sm text-gray-600">{day.label}</span>
                        <div className="flex rounded-lg border border-gray-200 overflow-hidden text-xs">
                          {[{ val: 'gpx', label: 'Trasa GPX' }, { val: 'rest', label: 'Odpoczynek' }].map(({ val, label }) => (
                            <button key={val} type="button"
                              onClick={() => { setDayTypes(p => ({ ...p, [day.dayNumber]: val })); if (val === 'rest') setDayFiles(p => ({ ...p, [day.dayNumber]: null })); }}
                              className={`px-3 py-1 transition-colors ${(isRest ? val === 'rest' : val === 'gpx') ? 'bg-mountain-600 text-white' : 'text-gray-500 hover:bg-gray-100'}`}>
                              {label}
                            </button>
                          ))}
                        </div>
                      </div>
                      {!isRest && (
                        <label className="flex items-center gap-2 cursor-pointer">
                          <div className={`flex-1 text-xs px-2 py-1.5 rounded border text-center transition-colors ${
                            dayFiles[day.dayNumber]
                              ? 'border-mountain-400 text-mountain-700 bg-mountain-50'
                              : 'border-gray-200 text-gray-400 bg-white hover:bg-gray-50'
                          }`}>
                            {dayFiles[day.dayNumber] ? dayFiles[day.dayNumber].name : 'Wybierz plik .gpx'}
                          </div>
                          <input type="file" accept=".gpx" className="hidden"
                            onChange={e => handleDayFileChange(day.dayNumber, e)} />
                          {dayFiles[day.dayNumber] && (
                            <button type="button"
                              onClick={() => setDayFiles(prev => ({ ...prev, [day.dayNumber]: null }))}
                              className="text-gray-400 hover:text-red-400 text-xs shrink-0">
                              Usuń
                            </button>
                          )}
                        </label>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Opis */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Opis (opcjonalnie)</label>
            <textarea
              value={form.description}
              onChange={e => setForm({ ...form, description: e.target.value })}
              rows={3}
              className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400 resize-none"
              placeholder="Dodatkowe informacje dla uczestników..."
            />
          </div>

          {/* Widoczność */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Widoczność wyprawy</label>
            <VisibilitySelector value={form.visibility} onChange={v => setForm({ ...form, visibility: v })} />
          </div>

          {/* Dołączanie */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Dołączanie uczestników</label>
            <RadioGroup
              name="joinMode"
              options={JOIN_OPTIONS}
              value={form.joinMode}
              onChange={v => setForm({ ...form, joinMode: v })}
            />
          </div>

          {/* Sprzęt */}
          <div>
            <button
              type="button"
              onClick={() => setEquipmentOpen(o => !o)}
              className="w-full flex items-center justify-between text-sm font-medium text-gray-700 py-2 border-b border-gray-200"
            >
              <span>Sprzęt <span className="text-gray-400 font-normal">(opcjonalnie)</span></span>
              <span className="text-gray-400 text-xs">{equipmentOpen ? 'zwiń' : 'rozwiń'}</span>
            </button>
            {equipmentOpen && <div className="border border-gray-200 rounded-lg divide-y divide-gray-100 mt-2">
              {EQUIPMENT_ITEMS.map(item => (
                <div key={item.value} className="flex items-center justify-between px-3 py-2">
                  <span className="text-sm text-gray-700">{item.label}</span>
                  <div className="flex gap-1">
                    {[
                      { level: null,          label: 'brak',     active: 'bg-gray-100 text-gray-500' },
                      { level: 'REQUIRED',    label: 'wymagany', active: 'bg-red-100 text-red-700' },
                      { level: 'RECOMMENDED', label: 'zalecany', active: 'bg-amber-100 text-amber-700' },
                    ].map(opt => (
                      <button
                        key={String(opt.level)}
                        type="button"
                        onClick={() => setEquipment(prev => ({ ...prev, [item.value]: opt.level }))}
                        className={`text-xs px-2 py-1 rounded transition-colors ${
                          (equipment[item.value] ?? null) === opt.level
                            ? opt.active + ' font-medium'
                            : 'text-gray-400 hover:bg-gray-50'
                        }`}
                      >
                        {opt.label}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>}
          </div>

          <div className="flex gap-3 pt-2">
            <button
              type="submit" disabled={loading}
              className="flex-1 bg-mountain-600 hover:bg-mountain-700 disabled:bg-mountain-400 text-white py-2.5 rounded-lg font-medium transition-colors flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <svg className="animate-spin h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                  </svg>
                  {isMultiDay ? 'Tworzenie wyprawy...' : 'Parsowanie trasy...'}
                </>
              ) : 'Utwórz wyprawę'}
            </button>
            <Link to="/expeditions"
              className="px-6 py-2.5 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50 transition-colors text-center">
              Anuluj
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}
