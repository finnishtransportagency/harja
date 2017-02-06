(ns harja.domain.roolit
  "Harjan käyttäjäroolit"

  (:require
   [clojure.set :refer [intersection]]
    #?(:cljs [harja.tiedot.istunto :as istunto])
    #?(:cljs [harja.loki :as frontlog])
    #?(:clj [taoensso.timbre :as backlog])
    #?(:clj [slingshot.slingshot :refer [throw+]])))

(defrecord EiOikeutta [syy])

(defn ei-oikeutta? [arvo]
  (instance? EiOikeutta arvo))


;; Roolit kätevämpää käyttöä varten
(def jarjestelmavastuuhenkilo          "jarjestelmavastuuhenkilo") ;; UHA: jvh
(def tilaajan-kayttaja                 "tilaajan kayttaja")
(def urakanvalvoja                     "urakanvalvoja") ;; UHA: uv
;;(def vaylamuodon-vastuuhenkilo         "vaylamuodon vastuuhenkilo")
(def hallintayksikon-vastuuhenkilo     "hallintayksikon vastuuhenkilo") ;; UHA: vk
(def liikennepaivystaja                "liikennepaivystaja")
(def tilaajan-asiantuntija             "tilaajan asiantuntija")
(def tilaajan-laadunvalvontakonsultti  "tilaajan laadunvalvontakonsultti")
(def urakoitsijan-paakayttaja          "urakoitsijan paakayttaja")
(def urakoitsijan-urakan-vastuuhenkilo "urakoitsijan urakan vastuuhenkilo")
(def urakoitsijan-kayttaja             "urakoitsijan kayttaja")
(def urakoitsijan-laatuvastaava        "urakoitsijan laatuvastaava")
(def urakan-tiemerkitsija              "urakan tiemerkitsija")

;; Rooleja mitä tuodaan UHA:sta (Liito, Urre, rakkaalla lapselle monta nimeä)
(def liikennekeskuspaivystaja           "liikennekeskuspaivystaja") ;; UHA: lkp
(def urakoitsijan-paivystaja           "urakoitsijan paivystaja") ;; UHA: uy
(def raportoija-tiehallinto            "raportoija tiehallinto") ;; UHA: r

;; Sähke roolit
(def jarjestelmavastaava "Jarjestelmavastaava")




;; Esimääriteltyjä settejä rooleista
(def urakoitsijan-urakkaroolit-kirjoitus #{urakoitsijan-paakayttaja urakoitsijan-urakan-vastuuhenkilo
                                           urakoitsijan-laatuvastaava})


(defn- lokita [& sisalto]
  #?(:clj
     (backlog/log :debug [sisalto])
     :cljs
     (apply frontlog/log sisalto)))


;; YHTEISET
(def paallystysaikataulun-kirjaus
  "Roolit, joilla on oikeus muuttaa päällystysaikatauluja"
  #{urakoitsijan-paakayttaja
    urakoitsijan-urakan-vastuuhenkilo})


(def toteumien-kirjaus
  "Roolit, joilla on oikeus kirjoittaa urakkaan toteumatietoja."
  #{urakanvalvoja
    urakoitsijan-paakayttaja
    urakoitsijan-urakan-vastuuhenkilo
    urakoitsijan-laatuvastaava})

(def laadunseuranta-kirjaus
  "Roolit, joilla on oikeus kirjata laadunseurantaa urakkaan."
  #{urakanvalvoja
    urakoitsijan-paakayttaja
    urakoitsijan-urakan-vastuuhenkilo
    urakoitsijan-laatuvastaava
    tilaajan-laadunvalvontakonsultti})

(def kayttajien-hallinta
  "Roolit joilla on oikeus hallita käyttäjiä"
  #{jarjestelmavastuuhenkilo, tilaajan-kayttaja,
    urakanvalvoja, urakoitsijan-paakayttaja})

;; Tietokannan rooli enumin selvempi kuvaus
(def +rooli->kuvaus+
  {"jarjestelmavastuuhenkilo" "Järjestelmävastuuhenkilö"
   "tilaajan kayttaja" " Tilaajan käyttäjä"
   "urakanvalvoja" "Urakanvalvoja"
   ;;"vaylamuodon vastuuhenkilo" "Väylämuodon vastuuhenkilö"
   "hallintayksikon vastuuhenkilo" "Hallintayksikön vastuuhenkilö"
   "liikennepäivystäjä" "Liikennepäivystäjä"
   "tilaajan asiantuntija" "Tilaajan asiantuntija"
   "tilaajan laadunvalvontakonsultti" "Tilaajan laadunvalvontakonsultti"
   "urakoitsijan paakayttaja" "Urakoitsijan pääkäyttäjä"
   "urakoitsijan urakan vastuuhenkilo" "Urakoitsijan urakan vastuuhenkilö"
   "urakoitsijan kayttaja" "Urakoitsijan käyttäjä"
   "urakoitsijan laatuvastaava" "Urakoitsijan laatuvastaava"})

(defn rooli->kuvaus
  "Antaa roolin ihmisen luettavan kuvauksen käyttöliittymää varten."
  [rooli]
  (get +rooli->kuvaus+ rooli))


(defn urakkaroolit
  "Palauttaa setin rooleja, joita käyttäjällä on annetussa urakassa."
  #?(:cljs ([urakka-id] (urakkaroolit @istunto/kayttaja urakka-id)))
  ([{r :urakkaroolit} urakka-id]
   (or (get r urakka-id) #{})))

(defn organisaatioroolit
  "Palauttaa setin rooleja, joita käyttäjällä on annetussa organisaatiossa."
  ([kayttaja]
   (organisaatioroolit kayttaja (get-in kayttaja [:organisaatio :id])))
  ([kayttaja organisaatio-id]
   (get (:organisaatioroolit kayttaja) organisaatio-id)))

(defn organisaation-urakka?
  "Tarkistaa onko annettu urakka käyttäjän organisaation oma urakka.
Oma urakka on urakka, jossa käyttäjän organisaatio on hallintayksikkö tai
urakoitsija."
  [{urakat :organisaation-urakat} urakka-id]
  (and urakat
       (urakat urakka-id)))

(defn roolissa?
  "Tarkistaa onko käyttäjällä tietty rooli. Rooli voi olla joko yksittäinen rooli
tai setti rooleja. Jos annetaan setti, tarkistetaan onko käyttäjällä joku annetuista
rooleista."
  #?(:cljs ([rooli] (roolissa? @istunto/kayttaja rooli)))
  ([kayttaja rooli]
   (let [roolit (if (set? rooli)
                  rooli
                  #{rooli})]
     (if (or (some roolit (:roolit kayttaja))
             (some (fn [organisaatioroolit]
                     (some roolit organisaatioroolit))
                   (vals (:organisaatioroolit kayttaja))))
       true
       false))))

(defn jvh? [kayttaja]
  (roolissa? kayttaja jarjestelmavastaava))

(defn rooli-urakassa?
  "Tarkistaa onko käyttäjällä tietty rooli urakassa."
  #?(:cljs ([rooli urakka-id] (rooli-urakassa? @istunto/kayttaja rooli urakka-id)))
  ([kayttaja rooli urakka-id]
   (let [roolit (if (set? rooli)
                  rooli
                  #{rooli})]
     (or
      ;; Jos käyttäjällä on suoraan rooli annetussa urakassa
      (some roolit (urakkaroolit kayttaja urakka-id))

      ;; Tai käyttäjällä on rooli organisaatiossa ja urakka on organisaation urakka
      (and (organisaation-urakka? kayttaja urakka-id)
           (some roolit (organisaatioroolit kayttaja)))))))

(defn rooli-jossain-urakassa?
  "Tarkistaa onko käyttäjällä tietty rooli jossain urakassa (ei väliä missä)."
  #?(:cljs ([rooli] (rooli-jossain-urakassa? @istunto/kayttaja rooli)))
  ([kayttaja rooli]
   (some (partial rooli-urakassa? kayttaja rooli)
         (keys (:urakkaroolit kayttaja)))))

;; VAIN BACKILLÄ

#?(:clj
   (defn vaadi-rooli
     [kayttaja rooli]
     (when-not (roolissa? kayttaja rooli)
       (let [viesti (format "Käyttäjällä '%1$s' ei vaadittua roolia '%2$s'", (:kayttajanimi kayttaja) rooli)]
         (backlog/warn viesti)
         (throw+ (->EiOikeutta viesti))))))

#?(:clj
   (defn vaadi-rooli-urakassa
     [kayttaja rooli urakka-id]
     (when-not (rooli-urakassa? kayttaja rooli urakka-id)
       (let [viesti (format "Käyttäjällä '%1$s' ei vaadittua roolia '%2$s' urakassa jonka id on %3$s",
                            (:kayttajanimi kayttaja) rooli urakka-id)]
         (backlog/warn viesti)
         (throw+ (->EiOikeutta viesti))))))

(defn voi-kirjata-toteumia?
  "Käyttäjä voi kirjata toteumia, jos hänellä on toteumien kirjauksen rooli
  tai jos hän on urakan urakoitsijaorganisaation pääkäyttäjä"
  #?(:cljs ([urakka-id] (voi-kirjata-toteumia? @istunto/kayttaja urakka-id)))
  ([kayttaja urakka-id]
   (or (rooli-urakassa? kayttaja toteumien-kirjaus urakka-id)
       (and (organisaation-urakka? kayttaja urakka-id)
            (roolissa? kayttaja urakoitsijan-paakayttaja)))))

(defn lukuoikeus-kaikkiin-urakoihin?
  "Käyttäjä voi nähdä kaikki urakat, jos hän on tilaajaorganisaation edustaja (ELY tai LIVI)"
  #?(:cljs ([] (lukuoikeus-kaikkiin-urakoihin? @istunto/kayttaja)))
  ([kayttaja]
   (roolissa? kayttaja jarjestelmavastaava)))

(defn osapuoli
  "Päättelee kuka osapuoli on kyseessä käyttäjän organisaation perusteella.
   Palauttaa avainsanan :urakoitsija, :konsultti tai :tilaaja."
  [kayttaja]
  (case (get-in kayttaja [:organisaatio :tyyppi])
    "liikennevirasto" :tilaaja
    "urakoitsija" :urakoitsija
    ;; FIXME: laadunvalvontakonsultti ?
    :tilaaja))

(defn tilaajan-kayttaja?
  [kayttaja]
  (= :tilaaja (osapuoli kayttaja)))

(defn urakoitsija?
  [kayttaja]
  (= :urakoitsija (osapuoli kayttaja)))

(defn voi-nahda-raportit?
  "Käyttäjä voi nähdä raportit, jos hän on tilaajaorganisaation edustaja (ELY tai LIVI)"
  #?(:cljs ([] (voi-nahda-raportit? @istunto/kayttaja)))
  ([kayttaja]
   (tilaajan-kayttaja? kayttaja)))

;;VAIN FRONTILLA

#?(:cljs
   (defn jos-rooli-urakassa
     "Palauttaa komponentin käyttöliittymään jos käyttäjän rooli sallii.
     Palauttaa muutoin-komponentin jos ei kyseistä roolia."
     ([rooli urakka-id sitten] (jos-rooli-urakassa rooli urakka-id sitten nil))
     ([rooli urakka-id sitten muutoin]
       ;; ei onnistunut 2 arityllä kutsua rooli-urakassa
      (if (rooli-urakassa? @istunto/kayttaja rooli urakka-id)
        sitten
        (let [viesti (str "Käyttäjällä '" (:kayttajanimi @istunto/kayttaja) "' ei vaadittua roolia '" rooli "' urakassa " urakka-id)]
          (lokita viesti)
          muutoin)))))

#?(:cljs
   (defn jos-rooli
     "Palauttaa komponentin käyttöliittymään jos käyttäjän rooli sallii.
Palauttaa muutoin-komponentin jos ei kyseistä roolia. Annettu rooli voi olla
joko yksittäinen rooli tai joukko useita rooleja. Jos joukko, tarkistetaan että
käyttäjällä on joku annetuista rooleista."
     ([rooli sitten] (jos-rooli rooli sitten nil))
     ([rooli sitten muutoin]
      (if (and @istunto/kayttaja
               (or (and (set? rooli)
                        (some roolissa? rooli))
                   (roolissa? rooli)))
        sitten
        (let [viesti (str "Käyttäjällä '" (:kayttajanimi @istunto/kayttaja) "' ei vaadittua roolia '" rooli)]
          (lokita viesti)
          muutoin)))))
