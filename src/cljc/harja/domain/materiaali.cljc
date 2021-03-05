(ns harja.domain.materiaali)

(defn materiaalien-jarjestys
  "Asiakkaan suosima järjestys, jossa suolat ovat peräkkäin"
  [materiaali-nimi]
  (case materiaali-nimi
    "Kaikki talvisuola yhteensä" 0
    "Talvisuola" 1
    "Talvisuolaliuos CaCl2" 2
    "Talvisuolaliuos NaCl" 3
    "Talvisuola NaCl, rakeinen" 4
    "Erityisalueet CaCl2-liuos" 4.5
    "Erityisalueet NaCl" 5
    "Erityisalueet NaCl-liuos" 6
    "Hiekoitushiekan suola" 7
    "Kaliumformiaatti" 8
    "Natriumformiaatti" 9
    "Natriumformiaattiliuos" 9.5
    "Kesäsuola (pölynsidonta)" 10
    "Kesäsuola (sorateiden kevätkunnostus)" 11
    "Hiekoitushiekka" 12
    "Jätteet kaatopaikalle" 13
    "Murskeet" 14
    "Rikkaruohojen torjunta-aineet" 15
    16))
