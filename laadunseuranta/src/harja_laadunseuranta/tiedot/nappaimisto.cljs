(ns harja-laadunseuranta.tiedot.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.utils :refer [timestamp ipad?]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [cljs-time.local :as lt]
            [cljs-time.coerce :as tc]
            [harja-laadunseuranta.tiedot.math :as math]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [harja-laadunseuranta.tiedot.ilmoitukset :as ilmoitukset]
            [clojure.string :as str]))

;; Näppäimistön toiminnallisuus

(def syottosaannot {:kitkamittaus {:lahtoarvo "0,"
                                   :rajat [0.01 0.99]
                                   :kokonaisosan-merkkimaara 1
                                   :desimaaliosan-merkkimaara 2
                                   :salli-syottaa-desimaalierotin? false} ;; On jo mukana lähtöarvossa
                    :lumisuus {:lahtoarvo ""
                               :rajat [0 100]
                               :kokonaisosan-merkkimaara 3
                               :desimaaliosan-merkkimaara 0
                               :salli-syottaa-desimaalierotin? false}
                    :talvihoito-tasaisuus {:lahtoarvo ""
                                           :rajat [0 100]
                                           :kokonaisosan-merkkimaara 3
                                           :desimaaliosan-merkkimaara 1
                                           :salli-syottaa-desimaalierotin? true}})

(defn nykyisen-syotto-osan-max-merkkimaara-saavutettu? [mittaustyyppi nykyinen-syotto]
  (let [syoton-tyyppi (if (str/includes? nykyinen-syotto ",") :desimaaliosa :kokonaisosa)
        kokonaisosan-merkkimaara (get-in syottosaannot [mittaustyyppi :kokonaisosan-merkkimaara])
        desimaaliosan-merkkimaara (get-in syottosaannot [mittaustyyppi :desimaaliosan-merkkimaara])
        syotetty-kokonaisosa (first (str/split nykyinen-syotto #","))
        syotetty-desimaaliosa (second (str/split nykyinen-syotto #","))
        max-maara? (case syoton-tyyppi
                     :desimaaliosa
                     (>= (count syotetty-desimaaliosa) desimaaliosan-merkkimaara)
                     :kokonaisosa
                     (>= (count syotetty-kokonaisosa) kokonaisosan-merkkimaara))]
    max-maara?))

(defn syotto-validi?
  "Kertoo, onko annettu syöttö validi eli voidaanko se kirjata, paluuarvo true tai false.
   Optioilla voidaan määrittää, mitkä kaikki syöttösäännöt tarkistetaan (oletuksena kaikki true)."
  ([mittaustyyppi nykyinen-syotto] (syotto-validi? mittaustyyppi nykyinen-syotto {:validoi-rajat? true}))
  ([mittaustyyppi nykyinen-syotto {:keys [validoi-rajat?] :as optiot}]
   (let [kokonaisosan-merkkimaara (get-in syottosaannot [mittaustyyppi :kokonaisosan-merkkimaara])
         desimaaliosan-merkkimaara (get-in syottosaannot [mittaustyyppi :desimaaliosan-merkkimaara])
         syotetty-kokonaisosa (first (str/split nykyinen-syotto #","))
         syotetty-desimaaliosa (second (str/split nykyinen-syotto #","))
         syotto-sallittu?
         (boolean (and
                    ;; Merkkimäärät eivät ylity
                    (and (<= (count syotetty-kokonaisosa) kokonaisosan-merkkimaara)
                         (or (nil? syotetty-desimaaliosa)
                             (<= (count syotetty-desimaaliosa) desimaaliosan-merkkimaara)))
                    ;; Pilkku ei ole väärässä paikassa
                    (or (not (str/includes? nykyinen-syotto ","))
                        (and (str/includes? nykyinen-syotto ",")
                             (not= (str (first nykyinen-syotto)) ",")
                             (not= (str (last nykyinen-syotto)) ",")))
                    ;; Raja-arvot eivät ylity
                    (or (not validoi-rajat?)
                        (>= (fmt/string->numero nykyinen-syotto)
                            (first (get-in syottosaannot [mittaustyyppi :rajat]))))
                    (or (not validoi-rajat?)
                        (<= (fmt/string->numero nykyinen-syotto)
                            (second (get-in syottosaannot [mittaustyyppi :rajat]))))))]
     (.log js/console "Syöttö sallittu? " (pr-str syotto-sallittu?))
     syotto-sallittu?)))

(defn numeronappain-painettu!
  "Lisää syötteen syöttö-atomiin.
   Estää liian pitkät syötteet, mutta ei tarkista esim.
   min-max rajoja ylittäviä syötteitä. Näistä on tarkoitus
   näyttää varoitus käyttöliittymässä ja käyttäjää
   hyväksymästä tällaista syötettä."
  [numero mittaustyyppi syotto-atom]
  (.log js/console "Numero syötetty: " (pr-str numero))
  (let [nykyinen-syotto (:nykyinen-syotto @syotto-atom)
        uusi-syotto (str nykyinen-syotto numero)
        salli-syotto? (syotto-validi? mittaustyyppi uusi-syotto {:validoi-rajat? false})
        lopullinen-syotto (if salli-syotto?
                            uusi-syotto
                            nykyinen-syotto)]
    (swap! syotto-atom assoc :nykyinen-syotto lopullinen-syotto)))

(defn desimaalierotin-painettu!
  "Lisää desimaalierottimen syöttö-atomiin"
  [syotto-atom]
  (let [nykyinen-syotto (:nykyinen-syotto @syotto-atom)
        uusi-syotto (str nykyinen-syotto ",")
        salli-syotto? (not (str/includes? nykyinen-syotto ","))
        lopullinen-syotto (if salli-syotto?
                            uusi-syotto
                            nykyinen-syotto)]
    (swap! syotto-atom assoc :nykyinen-syotto lopullinen-syotto)))

(defn alusta-mittaussyotto! [mittaustyyppi syotto-atom]
  (swap! syotto-atom assoc :nykyinen-syotto (get-in syottosaannot [mittaustyyppi :lahtoarvo])))

(defn tyhjennyspainike-painettu! [mittaustyyppi syotto-atom]
  (let [poista-viimeinen-merkki #(apply str (butlast %))
        poiston-jalkeen (poista-viimeinen-merkki (:nykyinen-syotto @syotto-atom))
        mittaustyypin-alustusarvo (get-in syottosaannot [mittaustyyppi :lahtoarvo])
        uusi-syotto (if (< (count poiston-jalkeen) (count mittaustyypin-alustusarvo))
                      mittaustyypin-alustusarvo
                      poiston-jalkeen)]
    (swap! syotto-atom assoc :nykyinen-syotto uusi-syotto)))

(defn syotto-onnistui! [mittaustyyppi syotto-atom]
  (swap! syotto-atom assoc :syotot (conj (:syotot @syotto-atom) (:nykyinen-syotto @syotto-atom)))
  (swap! syotto-atom assoc :nykyinen-syotto (get-in syottosaannot [mittaustyyppi :lahtoarvo]))
  (.log js/console "Syötöt nyt: " (pr-str (:syotot @syotto-atom))))

(defn lopeta-mittaus-painettu! [nimi avain]
  (s/poista-jatkuva-havainto! avain)
  (s/aseta-mittaus-pois!)
  (ilmoitukset/ilmoita
    (str nimi " päättyy")
    s/ilmoitus))

;; Erikoisnäppäimistöt

(defn alusta-soratiemittaussyotto! [syotto-atom]
  (swap! syotto-atom assoc :tasaisuus 5)
  (swap! syotto-atom assoc :kiinteys 5)
  (swap! syotto-atom assoc :polyavyys 5))

(defn soratienappaimiston-numeronappain-painettu! [arvo mittaustyyppi syotto-atom]
  (.log js/console (pr-str "Painoit " mittaustyyppi " arvoksi " arvo))
  (swap! syotto-atom assoc mittaustyyppi arvo))

;; Arvojen kirjaaminen

(defn kirjaa-mittaus! [arvo]
  (reitintallennus/kirjaa-mittausarvo! {:idxdb @s/idxdb
                                        :sijainti @s/sijainti
                                        :tarkastusajo-id @s/tarkastusajo-id
                                        :jatkuvat-havainnot @s/jatkuvat-havainnot
                                        :mittaustyyppi @s/mittaustyyppi
                                        :mittausarvo arvo
                                        :epaonnistui-fn reitintallennus/merkinta-epaonnistui}))
