(ns harja.domain.hoitoluokat
  "Määrittelee talvihoitoluokat ja soratien hoitoluokat")

(def ^{:doc "Mahdolliset talvihoitoluokat. Nimi kertoo käyttöliittymässä käytetyn
nimen. Numero on tierekisterin koodi luokalle."}
  talvihoitoluokat
  [{:nimi "Is"  :numero 1 :numero-str "1"}
   {:nimi "I"   :numero 2 :numero-str "2"}
   {:nimi "Ib"  :numero 3 :numero-str "3"}
   {:nimi "TIb" :numero 4 :numero-str "4"}
   {:nimi "II"  :numero 5 :numero-str "5"}
   {:nimi "III" :numero 6 :numero-str "6"}
   {:nimi "K1"  :numero 7 :numero-str "7"}
   {:nimi "K2"  :numero 8 :numero-str "8"}])

(def ei-talvihoitoluokkaa-nimi "Ei tiedossa")

(def ^{:doc "Mahdolliset talvihoitoluokat ja tuntematon"}
  talvihoitoluokat-ja-tuntematon
  (conj talvihoitoluokat {:nimi ei-talvihoitoluokkaa-nimi  :numero 0 :numero-str "0"}))



(defn haluttujen-hoitoluokkien-nimet-ja-numerot [hoitoluokan-numero-set]
  (conj
    (vec (filter #(hoitoluokan-numero-set (:numero %)) talvihoitoluokat))
    {:nimi   ei-talvihoitoluokkaa-nimi
     :numero nil}
    ;; Joissain kyselyissä palautetaan toteumille talvihoitoluokat.
    ;; Jos talvihoitoluokkaa ei ole, :numero on nil. Kun :numero on täälläkin
    ;; nil, saadaan nämä kaksi liitettyä yhteen.
    ))

(def ^{:doc "Mäppäys talvihoitoluokan numerosta sen nimeen."}
  talvihoitoluokan-nimi
  (into {} (map (juxt :numero :nimi)) talvihoitoluokat-ja-tuntematon))

(def ^{:doc "Mäppäys talvihoitoluokan numerosta (stringinä) sen nimeen."}
talvihoitoluokan-nimi-str
  (into {} (map (juxt :numero-str :nimi)) talvihoitoluokat-ja-tuntematon))

(def ^{:doc "Mäppäys talvihoitoluokan nimestä sen numeroon."}
  talvihoitoluokan-numero
  (into {} (map (juxt :nimi :numero)) talvihoitoluokat-ja-tuntematon))

(def ^{:doc "Mahdolliset soratieluokat. Nimi kertoo käyttöliittymässä käytetyn nimen.
Numero on tierekisterin koodi luokalle."}
  soratieluokat
  [{:nimi "I" :kuvaus "I lk Vilkkaat soratiet" :numero 1}
   {:nimi "II" :kuvaus "II lk Perussoratiet" :numero 2}
   {:nimi "III" :kuvaus "III lk Vähäliikenteiset soratiet" :numero 3}])

(def ^{:doc "Mäppäys soratieluokan numerosta sen nimeen."}
  soratieluokan-nimi
  (into {} (map (juxt :numero :nimi)) soratieluokat))
