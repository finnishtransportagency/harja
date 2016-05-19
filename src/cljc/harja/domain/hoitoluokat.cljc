(ns harja.domain.hoitoluokat
  "Määrittelee talvihoitoluokat ja soratien hoitoluokat")

(def ^{:doc "Mahdolliset talvihoitoluokat. Nimi kertoo käyttöliittymässä käytetyn
nimen. Numero on tierekisterin koodi luokalle."}
  talvihoitoluokat
  [{:nimi "Is"  :numero 1}
   {:nimi "I"   :numero 2}
   {:nimi "Ib"  :numero 3}
   {:nimi "TIb" :numero 4}
   {:nimi "II"  :numero 5}
   {:nimi "III" :numero 6}
   {:nimi "K1"  :numero 7}
   {:nimi "K2"  :numero 8}])

(def ^{:doc "Mäppäys talvihoitoluokan numerosta sen nimeen."}
  talvihoitoluokan-nimi
  (into {} (map (juxt :numero :nimi)) talvihoitoluokat))


(def ^{:doc "Mäppäys talvihoitoluokan nimestä sen numeroon."}
  talvihoitoluokan-numero
  (into {} (map (juxt :nimi :numero)) talvihoitoluokat))

(def ^{:doc "Mahdolliset soratieluokat. Nimi kertoo käyttöliittymässä käytetyn nimen.
Numero on tierekisterin koodi luokalle."}
  soratieluokat
  [{:nimi "I" :kuvaus "I lk Vilkkaat soratiet" :numero 1}
   {:nimi "II" :kuvaus "II lk Perussoratiet" :numero 2}
   {:nimi "III" :kuvaus "III lk Vähäliikenteiset soratiet" :numero 3}])

(def ^{:doc "Mäppäys soratieluokan numerosta sen nimeen."}
  soratieluokan-nimi
  (into {} (map (juxt :numero :nimi)) soratieluokat))
