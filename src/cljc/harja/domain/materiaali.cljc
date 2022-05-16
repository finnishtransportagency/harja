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
    "Kaliumformiaattiliuos" 9
    "Natriumformiaatti" 10
    "Natriumformiaattiliuos" 10.5
    "Formiaatit yhteensä (50 % liuostonnia)" 11
    "Kesäsuola sorateiden kevätkunnostus" 12
    "Kesäsuola sorateiden pölynsidonta" 13
    "Kesäsuola päällystettyjen teiden pölynsidonta" 14
    "Kesäsuola yhteensä (t)" 15
    "Hiekoitushiekka" 16
    "Murskeet" 17
    "Sorastusmurske" 17.1
    "Kelirikkomurske" 17.2
    "Reunantäyttömurske" 17.3
    "Murskeet yhteensä (t)" 17.4
    "Jätteet kaatopaikalle" 18
    "Rikkaruohojen torjunta-aineet" 19
    16))
