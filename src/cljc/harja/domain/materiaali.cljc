(ns harja.domain.materiaali)

(defn materiaalien-jarjestys
  "Asiakkaan suosima järjestys, jossa suolat ovat peräkkäin"
  [materiaali-nimi]
  (case materiaali-nimi
    "Talvisuola, rakeinen NaCl" 1
    "Talvisuola NaCl, rakeinen" 1.1
    "Talvisuolaliuos CaCl2" 2
    "Talvisuolaliuos NaCl" 3
    "Hiekoitushiekan suola" 4
    "Erityisalueet CaCl2-liuos" 5
    "Erityisalueet NaCl" 6
    "Erityisalueet NaCl-liuos" 7
    "Talvisuolat yhteensä (100%) kuivatonnia" 8
    "Talvisuolat yhteensä" 8.1
    "Kaliumformiaattiliuos" 9
    "Natriumformiaatti" 10
    "Natriumformiaattiliuos" 11
    "Formiaatit yhteensä (50 % liuostonnia)" 12
    "Formiaatit yhteensä" 12.1
    "Kesäsuola sorateiden kevätkunnostus" 13
    "Kesäsuola sorateiden pölynsidonta" 14
    "Kesäsuola päällystettyjen teiden pölynsidonta" 15
    "Kesäsuola yhteensä (t)" 16
    "Hiekoitushiekka" 17
    "Murskeet" 18
    "Sorastusmurske" 19
    "Kelirikkomurske" 20
    "Reunantäyttömurske" 21
    "Murskeet yhteensä (t)" 22
    "Murskeet yhteensä" 22.1
    "Jätteet kaatopaikalle" 23
    "Rikkaruohojen torjunta-aineet" 24
    25))
