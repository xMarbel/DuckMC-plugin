# DuckMC – plugin (Paper 1.21.4, Java 21)

Wszystko w jednym pluginie: **patroni** (`/patron`), **rangi**, **scoreboard** (DUCKMC.PL), **NPC**, **miecze i sety** (`/sety`) + **paczka tekstur** (folder `resourcepack/`).

---------------------------------------------------------------------

## 1. Jak to zbudować przez GitHub (bez instalowania niczego)

1. Wejdź na github.com → **New repository** → nazwa np. `DuckMC` → Create.
2. Kliknij **uploading an existing file** i wrzuć **całą zawartość** tego folderu (razem z ukrytym folderem `.github`). Najprościej: rozpakuj ZIP i przeciągnij wszystko na stronę.
   Jeśli GitHub nie przyjmie folderu `.github`, stwórz plik ręcznie: *Add file → Create new file* → nazwa `.github/workflows/build.yml` i wklej zawartość.
3. Commit changes.
4. Zakładka **Actions** → workflow **Build DuckMC** buduje się sam (ok. 1–2 min). Jak nie ruszy: *Run workflow*.
5. Kliknij ukończony przebieg → na dole **Artifacts**:
   - `DuckMC-plugin` → w środku `DuckMC.jar`
   - `DuckMC-Pack` → w środku `DuckMC-Pack.zip` (paczka tekstur)
6. `DuckMC.jar` wrzuć do `plugins/` na serwerze Paper 1.21.4 i zrestartuj.

**Wersja stała (zalecana, żeby miał link do paczki tekstur):**
Zakładka *Releases → Create a new release* → tag `v1.0.0` → publish. Workflow sam dołączy `DuckMC.jar` i `DuckMC-Pack.zip` do release.
Link do paczki: prawy klik na `DuckMC-Pack.zip` w Release → *Kopiuj adres linku*.

---------------------------------------------------------------------

## 2. Paczka tekstur („txt”)

1. Wklej link do `DuckMC-Pack.zip` w `plugins/DuckMC/config.yml`:
   ```yaml
   resource-pack:
     enabled: true
     url: "TUTAJ_LINK"
   ```
2. `/duck reload` – gracze dostaną paczkę przy wejściu (`/duck paczka` wysyła ją od razu).
3. Co jest w paczce: 20 tekstur mieczy (`sword_1` … `sword_20`), ikony 8 patronów (`patron_*`), ikona Smoczego Odłamka.
4. **Własne, ładniejsze tekstury:** podmień pliki PNG 16×16 w
   `resourcepack/assets/duckmc/textures/item/` (zostaw te same nazwy), wrzuć na GitHub – workflow zbuduje nową paczkę.
   Tekstury, które wygenerowałem, są prostymi „zastępczymi” – podeślij screeny, to dorobimy lepsze.

Tekstury działają przez komponent `item_model` (1.21.4) – nie trzeba żadnych modów.

---------------------------------------------------------------------

## 3. Komendy

| Komenda | Opis |
|---|---|
| `/patron` | menu patrona (LPM = darmowe losowanie, PPM = szybkie za 2× Smoczy Odłamek) |
| `/sety` | sklep z zestawami (poziomy 1–20, płacisz kasą z konta) |
| `/ranga set <gracz> <ranga>` | nadaj rangę (`gracz, vip, svip, duck, mini-media, media, tworca`) |
| `/ranga lista` | lista rang |
| `/npc ...` | NPC (patrz niżej) |
| `/duck reload` | przeładuj wszystkie configi |
| `/duck zestaw <gracz> <poziom>` | daj cały zestaw |
| `/duck miecz <gracz> <poziom>` | daj sam miecz |
| `/duck odlamek <gracz> <ile>` | daj Smocze Odłamki |
| `/duck kasa give\|take\|set <gracz> <kwota>` | stan konta |
| `/duck stat <gracz> esencje\|srebrne\|zlote\|punkty\|klan <wartość>` | statystyki do scoreboardu |
| `/duck patron <gracz> <id\|none> [dni]` | ustaw patrona |
| `/duck godzina start <patron> [min]` / `stop` / `status` | ręczna Godzina Patrona (do testów) |
| `/duck paczka [gracz]` | wyślij paczkę tekstur |

Uprawnienie administratora: `duckmc.admin` (domyślnie OP).

---------------------------------------------------------------------

## 4. Patroni i Godziny Patronów

Patroni: **Cyklop, Helios, Kratos, Tanatos, Hades, Hefajstos, Artemida, Afrodyta** – każdy ma perk dla patrona i efekt w Godzinie Patrona (dokładnie jak na screenach). Wszystkie szanse i czasy są w `patrons.yml` (sekcja `values`).

- Godziny Patronów: pn–pt 13:00–22:30, weekend 10:00–23:30; losowanie o pełnej godzinie, trwa 30 min (zmienisz w `patrons.yml → settings`).
- Nowy patron wspiera gracza 5 dni, darmowe losowanie co 3 dni.
- Test bez czekania: `/duck godzina start hades 5`.

Perki w skrócie:
- **Cyklop** – szansa na spowolnienie; GP: złote jabłka dają większą regenerację
- **Helios** – 10% że złote jabłko da efekty „kokosa”; GP: szansa na podświetlenie przeciwnika
- **Kratos** – szansa na pajęczynę pod nogami; GP: blokada stawiania pajęczyn
- **Tanatos** – szansa na ściągnięcie hełmu; GP: cooldown na fajerwerki
- **Hades** – poniżej 5 serc więcej obrażeń; GP: szansa na extra uderzenie
- **Hefajstos** – szansa na zablokowanie uderzenia; GP: szansa na osłabienie
- **Artemida** – większy drop z kopania; GP: TurboDrop ×1.3 z generatorów dla wszystkich
- **Afrodyta** – pół dodatkowego serca; GP: +1 serce dla wszystkich

---------------------------------------------------------------------

## 5. Rangi

Plik `ranks.yml`. Kolory: Gracz – szary, VIP – żółty, SVIP – pomarańczowy, Duck – niebieski, Twórca – fioletowy, Media – różowy, Mini-Media – gradient fiolet→róż.
Ranga widoczna w czacie, nad głową, w tabie i na scoreboardzie. Nową rangę dodajesz kopiując blok w `ranks.yml`.

---------------------------------------------------------------------

## 6. Scoreboard (DUCKMC.PL)

Plik `scoreboard.yml` – edytujesz linie, kolory i tytuł (animowany gradient). Po zmianie `/duck reload`.
Placeholdery: `%kanal% %nick% %ranga% %patron% %stan% %kills% %deaths% %points% %pos% %czas% %esencje% %srebrne% %zlote% %klan% %online%`.
Wartości `esencje/srebrne/złote/klan` ustawiasz przez `/duck stat`, a kasa przez `/duck kasa` (plugin ma własną prostą ekonomię; zabójstwa dają punkty i kasę – `config.yml → points`).

---------------------------------------------------------------------

## 7. NPC (łatwa konfiguracja)

Stań tam, gdzie ma być NPC:
```
/npc create patron VILLAGER
/npc command patron patron          ← po kliknięciu wykona /patron
/npc setline patron 1 <gold><bold>PATRON
/npc setline patron 2 <gray>Kliknij we mnie aby wylosować patrona
```
Inne: `addline`, `clearlines`, `type`, `head` (ARMOR_STAND), `move`, `tp`, `remove`, `list`, `reload`.
Przykłady: NPC sklepu → `/npc command sklep sety`. Teksty nad głową używają MiniMessage (kolory `<#FF00FF>`, `<gradient:...>`).
NPC są zapisywane w `npcs.yml` i same się odtwarzają po restarcie.

---------------------------------------------------------------------

## 8. Miecze

Plik `swords.yml` – 20 poziomów. Mają nazwy i ceny z Twoich screenów dla poziomów 11–17 (Ares, Kratos, Posejdon, Hades, Zeus, Artemida, Satyr).
Poziomy 1–10 i 18–20 mają nazwy tymczasowe – **podeślij nazwy, wpiszę je** (albo zmień sam w `swords.yml`).
Miecz: Ostrość = 2·poziom+1, „Niezniszczalność” = 2·poziom, nieniszczalny. Zestaw = hełm, napierśnik, spodnie, buty, miecz, topór, łopata.

---------------------------------------------------------------------

## 9. Struktura repo

```
pom.xml
src/main/java/pl/duckmc/core/*.java
src/main/resources/ (plugin.yml, config.yml, ranks.yml, scoreboard.yml, patrons.yml, swords.yml, npcs.yml)
resourcepack/       ← paczka tekstur
.github/workflows/build.yml
```
