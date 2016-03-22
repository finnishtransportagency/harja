(ns harja.domain.materiaali)

(defn materiaalien-jarjestys
  "Asiakkaan suosima järjestys, jossa suolat ovat peräkkäin"
  [materiaali-nimi]
  (case materiaali-nimi
    "Talvisuola" 1
    "Talvisuolaliuos CaCl2" 2
    "Talvisuolaliuos NaCl" 3
    "Talvisuola NaCl, rakeinen" 4
    "Kesäsuola (pölynsidonta)" 6
    "Kesäsuola (sorateiden kevätkunnostus)" 7
    "Kaliumformiaatti" 5
    "Erityisalueet NaCl" 8
    "Erityisalueet NaCl-liuos" 9
    "Hiekoitushiekan suola" 10
    "Hiekoitushiekka" 11
    "Jätteet kaatopaikalle" 12
    "Murskeet" 13
    "Rikkaruohojen torjunta-aineet" 14
    15))