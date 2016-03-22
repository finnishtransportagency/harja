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
    "Hiekoitushiekan suola" 8
    "Kesäsuola (pölynsidonta)" 9
    "Kesäsuola (sorateiden kevätkunnostus)" 10
    "Hiekoitushiekka" 11
    "Jätteet kaatopaikalle" 12
    "Murskeet" 13
    "Rikkaruohojen torjunta-aineet" 14
    15))