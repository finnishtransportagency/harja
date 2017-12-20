(ns harja.domain.materiaali)

(defn materiaalien-jarjestys
  "Asiakkaan suosima järjestys, jossa suolat ovat peräkkäin"
  [materiaali-nimi]
  (case materiaali-nimi
    "Talvisuola" 1
    "Talvisuolaliuos CaCl2" 2
    "Talvisuolaliuos NaCl" 3
    "Talvisuola NaCl, rakeinen" 4
    "Erityisalueet NaCl" 5
    "Erityisalueet NaCl-liuos" 6
    "Kaliumformiaatti" 7
    "Natriumformiaatti" 8
    "Hiekoitushiekan suola" 9
    "Kesäsuola (pölynsidonta)" 10
    "Kesäsuola (sorateiden kevätkunnostus)" 11
    "Hiekoitushiekka" 12
    "Jätteet kaatopaikalle" 13
    "Murskeet" 14
    "Rikkaruohojen torjunta-aineet" 15
    16))