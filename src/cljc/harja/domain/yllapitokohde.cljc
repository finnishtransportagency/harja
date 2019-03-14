(ns harja.domain.yllapitokohde
  "Ylläpitokohteiden yhteisiä apureita"
  (:require
    [harja.tyokalut.spec-apurit :as spec-apurit]
    [clojure.string :as str]
    [clojure.set :as clj-set]
    [harja.domain.tierekisteri :as tr-domain]
    [harja.domain.nil :as nil-ns]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [harja.pvm :as pvm]
    #?@(:clj
        [
    [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
    [clojure.future :refer :all]
    [harja.pvm :as pvm]
    [clj-time.core :as t]
    [taoensso.timbre :as log]
    [clj-time.coerce :as c]])))

(s/def ::id ::spec-apurit/postgres-serial)
(s/def ::kohdenumero (s/nilable string?))
(s/def ::nimi string?)
(s/def ::kokonaishinta (s/and number?))

(def ^{:doc "Sisältää vain nykyisin käytössä olevat luokat 1,2 ja 3 (eli numerot 8, 9 ja 10)."}
nykyiset-yllapitoluokat
  [{:lyhyt-nimi "1" :nimi "Luokka 1" :numero 8}
   {:lyhyt-nimi "2" :nimi "Luokka 2" :numero 9}
   {:lyhyt-nimi "3" :nimi "Luokka 3" :numero 10}
   {:lyhyt-nimi "-" :nimi "Ei ylläpitoluokkaa" :numero nil}])

(def vanhat-yllapitoluokat ^{:doc "Sisältää vanhat ylläpitoluokat, tarvitaan YHA:n kanssa taakseepäinyhteensopivuuden vuoksi."}
[{:lyhyt-nimi "1a" :nimi "Luokka 1a" :numero 1}
 {:lyhyt-nimi "1b" :nimi "Luokka 1b" :numero 2}
 {:lyhyt-nimi "1c" :nimi "Luokka 1c" :numero 3}
 {:lyhyt-nimi "2a" :nimi "Luokka 2a" :numero 4}
 {:lyhyt-nimi "2b" :nimi "Luokka 2b" :numero 5}
 {:lyhyt-nimi "3a" :nimi "Luokka 3a" :numero 6}
 {:lyhyt-nimi "3b" :nimi "Luokka 3b" :numero 7}])

(def kaikki-yllapitoluokat (concat nykyiset-yllapitoluokat vanhat-yllapitoluokat))

(s/def ::yllapitoluokka (s/int-in (apply min (keep :numero kaikki-yllapitoluokat))
                                  (inc (apply max (keep :numero kaikki-yllapitoluokat)))))

(def ^{:doc "Mahdolliset ylläpitoluokat. Nimi kertoo käyttöliittymässä käytetyn
nimen. Numero on YHA:n koodi luokalle joka talletetaan myös Harjan kantaan.
2017 alkaen pyritään käyttämään enää luokkia 1,2 ja 3 (eli numerot 8, 9 ja 10), mutta
taaksenpäinyhteensopivuuden nimissä pidetään vanhatkin luokat koodistossa."}
yllapitoluokat
  (into [] kaikki-yllapitoluokat))


(def ^{:doc "Mäppäys ylläpitoluokan numerosta sen lyhyeen nimeen."}
yllapitoluokkanumero->lyhyt-nimi
  (into {} (map (juxt :numero :lyhyt-nimi)) yllapitoluokat))

(def ^{:doc "Mäppäys ylläpitoluokan numerosta sen kokonimeen."}
yllapitoluokkanumero->nimi
  (into {} (map (juxt :numero :nimi)) yllapitoluokat))

(def ^{:doc "Mäppäys ylläpitoluokan nimestä sen numeroon."}
yllapitoluokkanimi->numero
  (into {} (map (juxt :nimi :numero)) yllapitoluokat))

(def yllapitoluokka-xf
  (map #(assoc % :yllapitoluokka {:nimi (yllapitoluokkanumero->nimi (:yllapitoluokka %))
                                  :lyhyt-nimi (yllapitoluokkanumero->lyhyt-nimi (:yllapitoluokka %))
                                  :numero (:yllapitoluokka %)})))

(def +kohteissa-viallisia-sijainteja+ "viallisia-sijainteja")
(def +viallinen-yllapitokohteen-sijainti+ "viallinen-kohteen-sijainti")
(def +viallinen-yllapitokohdeosan-sijainti+ "viallinen-alikohteen-sijainti")
(def +viallinen-alustatoimenpiteen-sijainti+ "viallinen-alustatoimenpiteen-sijainti")

(defn kohdenumero-str->kohdenumero-vec
  "Palauttaa \"301b\":n muodossa [301 \"b\"]"
  [kohdenumero]
  (when kohdenumero
    (let [numero (re-find #"\d+" kohdenumero)
          kirjain (re-find #"\D+" kohdenumero)]
      [(when numero
         (#?(:clj  Integer.
             :cljs js/parseInt) numero))
       kirjain])))

(defn yllapitokohteen-jarjestys
  [kohde]
  ((juxt #(kohdenumero-str->kohdenumero-vec (:kohdenumero %))
         :tie :tr-numero :tienumero
         :ajr :tr-ajorata :ajorata
         :kaista :tr-kaista
         :aosa :tr-alkuosa
         :aet :tr-alkuetaisyys) kohde))

(defn- jarjesta-yllapitokohteet*
  [kohteet]
  (sort-by yllapitokohteen-jarjestys kohteet))

(defn jarjesta-yllapitokohteet [yllapitokohteet]
  (let [kohteet-kohdenumerolla (filter #(not (str/blank? (:kohdenumero %))) yllapitokohteet)
        kohteet-ilman-kohdenumeroa (filter #(str/blank? (:kohdenumero %)) yllapitokohteet)]
    (vec (concat (jarjesta-yllapitokohteet* kohteet-kohdenumerolla)
                 (tr-domain/jarjesta-tiet kohteet-ilman-kohdenumeroa)))))

#?(:clj
   (defn tee-virhe [koodi viesti]
     {:koodi koodi :viesti viesti}))

#?(:clj
   (defn validoi-sijainti [{:keys [aosa aet losa let] :as sijainti}]
     ;; Käytetään täydellä namespacella, jotta voidaan destrukturoida loppuetäisyys (let).
     (clojure.core/let [virhe (fn [viesti] (tee-virhe +viallinen-yllapitokohteen-sijainti+ (format viesti sijainti)))
                        negatiivinen? #(and % (> 0 %))
                        validaattorit [{:validaattori #(nil? aosa) :virhe (virhe "Alkuosa puuttuu. Sijainti: %s")}
                                       {:validaattori #(nil? aet) :virhe (virhe "Alkuetaisyys puuttuu. Sijainti: %s")}
                                       {:validaattori #(nil? losa) :virhe (virhe "Loppuosa puuttuu. Sijainti: %s")}
                                       {:validaattori #(nil? let) :virhe (virhe "Loppuetäisyys puuttuu. Sijainti: %s")}
                                       {:validaattori #(negatiivinen? aosa) :virhe (virhe "Alkuosa ei saa olla negatiivinen. Sijainti: %s")}
                                       {:validaattori #(negatiivinen? aet) :virhe (virhe "Alkuetäisyys ei saa olla negatiivinen. Sijainti: %s")}
                                       {:validaattori #(negatiivinen? losa) :virhe (virhe "Lopppuosa ei saa olla negatiivinen. Sijainti: %s")}
                                       {:validaattori #(negatiivinen? let) :virhe (virhe "Loppuetäisyys ei saa olla negatiivinen. Sijainti: %s")}
                                       {:validaattori #(> aosa losa) :virhe (virhe "Alkuosa on loppuosaa isompi. Sijainti: %s")}]]
       (keep (fn [{:keys [validaattori virhe]}]
               (when (validaattori) virhe)) validaattorit))))

#?(:clj
   (defn alikohde-kohteen-sisalla? [kohteen-sijainti alikohteen-sijainti]
     (and (<= (:aosa kohteen-sijainti) (:aosa alikohteen-sijainti))
          (or
            (not= (:aosa kohteen-sijainti) (:aosa alikohteen-sijainti))
            (<= (:aet kohteen-sijainti) (:aet alikohteen-sijainti)))
          (>= (:losa kohteen-sijainti) (:losa alikohteen-sijainti))
          (or (not= (:losa kohteen-sijainti) (:losa alikohteen-sijainti))
              (>= (:let kohteen-sijainti) (:let alikohteen-sijainti))))))

#?(:clj
   (defn tarkista-alikohteet-sisaltyvat-kohteeseen [kohde-id kohteen-sijainti alikohteet]
     (mapv (fn [{:keys [tunnus tunniste sijainti]}]
             (when (not (alikohde-kohteen-sisalla? kohteen-sijainti sijainti))
               (tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                          (format "Alikohde (tunniste: %s) ei ole kohteen (tunniste: %s) sisällä."
                                  (or tunnus (:id tunniste))
                                  kohde-id))))
           alikohteet)))

#?(:clj
   (defn tarkista-ajorata
     [sijainti]
     (or (:ajr sijainti) (:tr-ajorata sijainti) (:ajorata sijainti))))

#?(:clj
   (defn tarkista-kaista
     [sijainti]
     (or (:kaista sijainti) (:tr-kaista sijainti))))

#?(:clj
   (defn tarkista-alikohteiden-ajorata-ja-kaista
     "Tarkistaa, että alikohteella on ajorata ja kaista."
     [alikohteet]
     (mapv (fn [{:keys [tunnus tunniste sijainti]}]
             (when-not (and (tarkista-ajorata sijainti) (tarkista-kaista sijainti))
               (tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                          (str "Alikohteelta (tunniste: " (or tunnus (:id tunniste)) ") puuttuu "
                               (apply str
                                      (interpose ", " (keep (fn [{:keys [f nimi]}]
                                                              (when (nil? (f sijainti))
                                                                nimi))
                                                            [{:f tarkista-ajorata :nimi "ajorata"} {:f tarkista-kaista :nimi "kaista"}])))))))
           alikohteet)))

#?(:clj
   (defn tarkista-etteivat-alikohteet-mene-paallekkain
     "Tarkistaa, etteivät annetut alikohteet ole päällekäin toistensa kanssa."
     [alikohteet]
     (let [alikohteet (sort-by (comp yllapitokohteen-jarjestys :sijainti) alikohteet)
           lisaa-virhe (fn [edellinen seuraava]
                         (conj
                           (:virheet edellinen)
                           (tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                                      (format "Alikohteet (tunnus: %s ja tunnus: %s) menevät päällekkäin"
                                              (or (:tunnus (:edellinen edellinen) (get-in (:edellinen edellinen) [:tunniste :id])))
                                              (or (:tunnus seuraava) (get-in seuraava [:tunniste :id]))))))
           paallekkain? (fn [seuraava edellinen]
                          (let [edellinen-loppuosa (get-in edellinen [:edellinen :sijainti :losa])
                                seuraava-alkuosa (get-in seuraava [:sijainti :aosa])
                                edellinen-loppuetaisyys (get-in edellinen [:edellinen :sijainti :let])
                                seuraava-alkuetaisyys (get-in seuraava [:sijainti :aet])
                                edellinen-ajorata (or (get-in edellinen [:edellinen :sijainti :ajorata])
                                                      (get-in edellinen [:edellinen :sijainti :ajr]))
                                seuraava-ajorata (or (get-in seuraava [:sijainti :ajorata])
                                                     (get-in seuraava [:sijainti :ajr]))
                                edellinen-kaista (get-in edellinen [:edellinen :sijainti :kaista])
                                seuraava-kaista (get-in seuraava [:sijainti :kaista])]
                            (and
                              (= edellinen-loppuosa seuraava-alkuosa)
                              (= edellinen-ajorata seuraava-ajorata)
                              (= edellinen-kaista seuraava-kaista)
                              (> edellinen-loppuetaisyys seuraava-alkuetaisyys))))]
       (:virheet
         (reduce
           (fn [edellinen seuraava]
             (if (paallekkain? seuraava edellinen)
               {:edellinen seuraava
                :virheet (lisaa-virhe edellinen seuraava)}
               (assoc edellinen :edellinen seuraava)))
           {:virheet [] :edellinen (first alikohteet)}
           (rest alikohteet))))))

#?(:clj
   (defn tarkista-alikohteiden-sijainnit [alikohteet]
     (flatten (mapv #(validoi-sijainti (:sijainti %)) alikohteet))))

#?(:clj
   (defn validoi-alikohteet [kohde-id kohteen-sijainti alikohteet]
     (when alikohteet
       (concat
         (tarkista-alikohteiden-sijainnit alikohteet)
         (tarkista-alikohteet-sisaltyvat-kohteeseen kohde-id kohteen-sijainti alikohteet)
         (tarkista-alikohteiden-ajorata-ja-kaista alikohteet)
         (tarkista-etteivat-alikohteet-mene-paallekkain alikohteet)))))

(defn kohteeet-paallekkain? [kohde verrattava-kohde]
  (boolean
    (and (not= (:id kohde) (:id verrattava-kohde))
         (and (= (:tr-numero kohde) (:tr-numero verrattava-kohde))
              (= (:tr-ajorata kohde) (:tr-ajorata verrattava-kohde))
              (= (:tr-kaista kohde) (:tr-kaista verrattava-kohde)))
         (tr-domain/tr-vali-leikkaa-tr-valin? kohde verrattava-kohde))))

(defn validoi-tr-osan-pituus
  "Olettaa, että tr-osoite on oikeamuotoinen"
  [osan-pituudet-teille {:keys [tr-numero tr-ajorata tr-kaista tr-alkuosa tr-alkuetaisyys
                                tr-loppuosa tr-loppuetaisyys] :as kohde}]
  (when osan-pituudet-teille
    (let [osan-pituudet (osan-pituudet-teille tr-numero)]
      (cond-> {}
              (nil? osan-pituudet) (assoc :tr-numero :ei-olemassa)
              (not (contains? osan-pituudet tr-alkuosa)) (update :tr-alkuosa assoc :ei-olemassa tr-alkuosa)
              (not (contains? osan-pituudet tr-loppuosa)) (update :tr-loppuosa assoc :ei-olemassa tr-loppuosa)

              (and (contains? osan-pituudet tr-alkuosa)
                   tr-ajorata
                   (if (= tr-alkuosa tr-loppuosa)
                     (> (- tr-loppuetaisyys tr-alkuetaisyys)
                        (get-in osan-pituudet [tr-alkuosa tr-ajorata]))
                     (> (- (get-in osan-pituudet [tr-alkuosa :pituus]) tr-alkuetaisyys)
                        (get-in osan-pituudet [tr-alkuosa tr-ajorata])))) (update :tr-alkuetaisyys assoc :liian-iso-ajorata [tr-alkuetaisyys (get-in osan-pituudet [tr-alkuosa tr-ajorata])])
              (and (contains? osan-pituudet tr-loppuosa)
                   tr-ajorata
                   (if (= tr-alkuosa tr-loppuosa)
                     (> (- tr-loppuetaisyys tr-alkuetaisyys)
                        (get-in osan-pituudet [tr-loppuosa tr-ajorata]))
                     (> tr-loppuetaisyys
                        (get-in osan-pituudet [tr-loppuosa tr-ajorata])))) (update :tr-loppuetaisyys assoc :liian-iso-ajorata [tr-loppuetaisyys (get-in osan-pituudet [tr-loppuosa tr-ajorata])])

              (and (contains? osan-pituudet tr-alkuosa)
                   (> tr-alkuetaisyys (get-in osan-pituudet [tr-alkuosa :pituus]))) (update :tr-alkuetaisyys assoc :liian-iso-osa [tr-alkuetaisyys (get-in osan-pituudet [tr-alkuosa :pituus])])
              (and (contains? osan-pituudet tr-loppuosa)
                   (> tr-loppuetaisyys (get-in osan-pituudet [tr-loppuosa :pituus]))) (update :tr-loppuetaisyys assoc :liian-iso-osa [tr-loppuetaisyys (get-in osan-pituudet [tr-loppuosa :pituus])])))))

(defn losa>aosa? [{:keys [tr-alkuosa tr-loppuosa]}]
  (and tr-alkuosa tr-loppuosa (> tr-loppuosa tr-alkuosa)))

(defn let>aet? [{:keys [tr-alkuetaisyys tr-loppuetaisyys]}]
  (and tr-alkuetaisyys tr-loppuetaisyys
       (> tr-loppuetaisyys tr-alkuetaisyys)))

(defn losa=aosa? [{:keys [tr-alkuosa tr-loppuosa]}]
  (and tr-alkuosa tr-loppuosa
       (= tr-alkuosa tr-loppuosa)))

(defn let=aet? [{:keys [tr-alkuetaisyys tr-loppuetaisyys]}]
  (and tr-alkuetaisyys tr-loppuetaisyys
       (= tr-alkuetaisyys tr-loppuetaisyys)))

(defn tr-paalupiste-tr-paaluvalin-sisalla?
  "Tarkastaa onko tr-piste tr-väin sisällä. Olettaa, että annetut tr-piste ja tr-vali
   ovat oikeasti olemassa. Toisin sanoen, tälle funktiolle voi antaa vaikkapa tr-pisteeksi
   {:tr-alkuosa 10000000 :tr-alkuetaisyys -3.23} ja se antaa jonkun tuloksen ulos vaikka
   tuollaista tr-pistettä ei ole olemassa."
  ([tr-piste tr-vali] (tr-paalupiste-tr-paaluvalin-sisalla? tr-piste tr-vali true))
  ([tr-piste tr-vali sisaltaen-rajat?]
   (let [{aosa-piste :tr-alkuosa aet-piste :tr-alkuetaisyys} tr-piste
         {aosa-vali :tr-alkuosa aet-vali :tr-alkuetaisyys
          losa-vali :tr-loppuosa let-vali :tr-loppuetaisyys} tr-vali
         ;; Ajatuksena on luoda 1-D jana, jolta voi simppelisit katsoa onko piste kyseisellä janalla.
         ;; Eli kerrotan osa jollain isolla luvulla, joka on vähintään isompi kuin suurin etäisyys millään osalla.
         ;; Tähän lisätään etäisyys, jollonika meillä on jana ja piste.
         kerroin 10000000
         jana-alku (+ (* kerroin aosa-vali) aet-vali)
         jana-loppu (+ (* kerroin losa-vali) let-vali)
         piste (+ (* kerroin aosa-piste) aet-piste)]
     (if sisaltaen-rajat?
       (and (<= jana-alku piste)
            (>= jana-loppu piste))
       (and (< jana-alku piste)
            (> jana-loppu piste))))))

(defn tr-paaluvali-tr-paaluvalin-sisalla?
  "Tarkastaa onko tr-vali-2 tr-vali-1:sen sisalla. Palauttaa true myös jos tr-vali-1 on kokonaan tr-vali2:sen sisällä
   sekä argumentti kokonaan-sisalla? on false"
  ([tr-vali-1 tr-vali-2] (tr-paaluvali-tr-paaluvalin-sisalla? tr-vali-1 tr-vali-2 true))
  ([tr-vali-1 tr-vali-2 kokonaan-sisalla?]
   (let [tr-valin-alkupiste (select-keys tr-vali-2 #{:tr-alkuosa :tr-alkuetaisyys})
         tr-valin-loppupiste (-> tr-vali-2
                                 (select-keys #{:tr-loppuosa :tr-loppuetaisyys})
                                 (clj-set/rename-keys {:tr-loppuosa :tr-alkuosa
                                                       :tr-loppuetaisyys :tr-alkuetaisyys}))
         alkupiste-sisalla? (tr-paalupiste-tr-paaluvalin-sisalla? tr-valin-alkupiste tr-vali-1 kokonaan-sisalla?)
         loppupiste-sisalla? (tr-paalupiste-tr-paaluvalin-sisalla? tr-valin-loppupiste tr-vali-1 kokonaan-sisalla?)]
     (if kokonaan-sisalla?
       (and alkupiste-sisalla? loppupiste-sisalla?)
       (or alkupiste-sisalla? loppupiste-sisalla?
           (tr-paaluvali-tr-paaluvalin-sisalla? tr-vali-2 tr-vali-1 true))))))

(defn tr-paalupiste-tr-tiedon-mukainen?
  [paalupiste kohteen-tieto]
  (let [{tr-numero-kohde :tr-numero
         tr-alkuosa-kohde :tr-alkuosa} paalupiste
        {tr-numero-kohteen-tieto :tr-numero
         tr-osa-kohteen-tieto :tr-osa
         {tr-alkuetaisyys-kohteen-tieto :tr-alkuetaisyys
          tr-osa-pituus :pituus} :pituudet} kohteen-tieto
        tr-loppuetaisyys-kohteen-tieto (+ tr-alkuetaisyys-kohteen-tieto tr-osa-pituus)
        kohteen-tieto-vali {:tr-alkuosa tr-osa-kohteen-tieto
                            :tr-loppuosa tr-osa-kohteen-tieto
                            :tr-alkuetaisyys tr-alkuetaisyys-kohteen-tieto
                            :tr-loppuetaisyys tr-loppuetaisyys-kohteen-tieto}]
    (and (= tr-numero-kohde tr-numero-kohteen-tieto)
         (= tr-alkuosa-kohde tr-osa-kohteen-tieto)
         (tr-paalupiste-tr-paaluvalin-sisalla? paalupiste kohteen-tieto-vali))))

(defn tr-piste-tr-tiedon-mukainen?
  [piste kohteen-tieto]
  (let [{tr-numero-alikohde :tr-numero
         tr-ajorata-alikohde :ajorata
         tr-kaista-alikohde :kaista
         tr-alkuosa-alikohde :tr-alkuosa
         tr-alkuetaisyys-alikohde :tr-alkuetaisyys} piste
        {tr-numero-kohteen-tieto :tr-numero
         tr-osa-kohteen-tieto :tr-osa
         pituudet-kohteen-tieto :pituudet} kohteen-tieto
        valin-sisalla? #(and (>= tr-alkuetaisyys-alikohde (:tr-alkuetaisyys %))
                             (<= tr-alkuetaisyys-alikohde (+ (:tr-alkuetaisyys %)
                                                            (:pituus %))))
        ajorata-kohteen-tiedot (->> pituudet-kohteen-tieto :ajoradat (filter #(= (:ajorata %) tr-ajorata-alikohde)) first)
        kaista-kohteen-tiedot (->> ajorata-kohteen-tiedot
                                   :osiot
                                   (filter valin-sisalla?)
                                   first
                                   :kaistat
                                   (filter #(and (valin-sisalla? %)
                                                 (= (:kaista %) tr-kaista-alikohde)))
                                   first)
        {tr-kaista-kohteen-tieto :kaista
         tr-alkuetaisyys-kohteen-tieto :tr-alkuetaisyys
         tr-pituus-kohteen-tieto :pituus} kaista-kohteen-tiedot

        tr-loppuetaisyys-kohteen-tieto (+ tr-pituus-kohteen-tieto tr-alkuetaisyys-kohteen-tieto)
        tr-ajorata-kohteen-tieto (:ajorata ajorata-kohteen-tiedot)

        kohteen-tieto-vali {:tr-alkuosa tr-osa-kohteen-tieto
                            :tr-loppuosa tr-osa-kohteen-tieto
                            :tr-alkuetaisyys tr-alkuetaisyys-kohteen-tieto
                            :tr-loppuetaisyys tr-loppuetaisyys-kohteen-tieto}]
    (and (= tr-numero-alikohde tr-numero-kohteen-tieto)
         (= tr-ajorata-alikohde tr-ajorata-kohteen-tieto)
         (= tr-kaista-alikohde tr-kaista-kohteen-tieto)
         (= tr-alkuosa-alikohde tr-osa-kohteen-tieto)
         (tr-paalupiste-tr-paaluvalin-sisalla? piste kohteen-tieto-vali))))

(s/def ::positive-int? (s/and integer? #(>= % 0)))

(s/def ::tr-numero ::positive-int?)
(s/def ::ajorata #{0 1 2})
(s/def ::kaista #{1 11 12 13 14 15 21 22 23 24 25})
(s/def ::tr-alkuosa ::positive-int?)
(s/def ::tr-alkuetaisyys ::positive-int?)
(s/def ::tr-loppuosa ::positive-int?)
(s/def ::tr-loppuetaisyys ::positive-int?)

(s/def ::tr-paalupiste (s/and map?
                              (s/keys :req-un [::tr-numero
                                               ::tr-alkuosa
                                               ::tr-alkuetaisyys]
                                      :opt-un [::nil-ns/tr-loppuosa
                                               ::nil-ns/tr-loppuetaisyys])))

(s/def ::pelkka-tr-paalupiste (s/and ::tr-paalupiste
                                     (s/keys :opt-un [::nil-ns/ajorata
                                                      ::nil-ns/kaista])))

(s/def ::tr-piste (s/or :paalupiste ::pelkka-tr-paalupiste
                        :piste (s/and ::tr-paalupiste
                                      (s/keys :req-un [::ajorata
                                                       ::kaista]))))

(s/def ::tr-paaluvali (s/with-gen (s/and map?
                                         (s/keys :req-un [::tr-numero
                                                          ::tr-alkuosa
                                                          ::tr-alkuetaisyys
                                                          ::tr-loppuosa
                                                          ::tr-loppuetaisyys])
                                         #(or (losa>aosa? %)
                                              (losa=aosa? %))
                                         #(if (losa=aosa? %)
                                            (or (let>aet? %)
                                                (let=aet? %))
                                            true))
                                  #(gen/fmap (fn [[tr-numero tr-alkuosa tr-alkuetaisyys]]
                                               (let [semi-random-int (fn [n]
                                                                       ;; Eli ideana tässä on se, että 50% todennäköisyydellä
                                                                       ;; saadaan 0 (tämä siksi, että saadaan samalla osalla olevia
                                                                       ;; tr-osotteita todennäköisesti)
                                                                       (if (= 0 (rand-int 2))
                                                                         0
                                                                         (rand-int n)))
                                                     tr-loppuosa (+ tr-alkuosa (semi-random-int 50))
                                                     tr-loppuetaisyys (+ tr-alkuetaisyys (semi-random-int 1000))]
                                                 {:tr-numero tr-numero :tr-alkuosa tr-alkuosa :tr-alkuetaisyys tr-alkuetaisyys
                                                  :tr-loppuosa tr-loppuosa :tr-loppuetaisyys tr-loppuetaisyys}))
                                             (gen/tuple (s/gen (s/and ::tr-numero
                                                                      (fn [n]
                                                                        (> 10000 n))))
                                                        (s/gen (s/and ::tr-alkuosa
                                                                      (fn [n]
                                                                        (> 100 n))))
                                                        (s/gen (s/and ::tr-alkuetaisyys
                                                                      (fn [n]
                                                                        (> 10000 n))))))))

(s/def ::pelkka-tr-paaluvali (s/and ::tr-paaluvali
                                    (s/keys :opt-un [::nil-ns/ajorata
                                                     ::nil-ns/kaista])))

(s/def ::tr-vali (s/or :paaluvali ::pelkka-tr-paaluvali
                       :vali (s/and ::tr-paaluvali
                                    (s/keys :req-un [::ajorata
                                                     ::kaista]))))

(s/def ::tr (s/or :vali ::tr-vali
                  :piste ::tr-piste))

(s/def ::kohteen-tieto map?)

(s/def ::kohteen-tiedot (s/coll-of ::kohteen-tieto :min-count 1 :max-count 2))

(s/def ::alikohde-osion-sisalla
  (s/and (s/cat :alikohde (s/and ::tr-paaluvali
                                 (s/keys :req-un [::ajorata
                                                  ::kaista]))
                :kohteen-tiedot ::kohteen-tieto)
         #(let [{:keys [alikohde kohteen-tiedot]} %
                piste (dissoc alikohde :tr-loppuosa :tr-loppuetaisyys)]
            (tr-piste-tr-tiedon-mukainen? piste kohteen-tiedot))))

(s/def ::paakohde-osan-sisalla
  (s/and (s/cat :kohde ::pelkka-tr-paaluvali :kohteen-tiedot ::kohteen-tieto)
         #(let [{:keys [kohde kohteen-tiedot]} %
                paalupiste (dissoc kohde :tr-loppuosa :tr-loppuetaisyys)]
            (tr-paalupiste-tr-tiedon-mukainen? paalupiste kohteen-tiedot))
         #(let [{:keys [kohde kohteen-tiedot]} %
                paalupiste (-> kohde
                               (dissoc :tr-alkuosa :tr-alkuetaisyys)
                               (clj-set/rename-keys {:tr-loppuosa :tr-alkuosa
                                                     :tr-loppuetaisyys :tr-alkuetaisyys}))]
            (tr-paalupiste-tr-tiedon-mukainen? paalupiste kohteen-tiedot))))

(s/def ::paakohde-osien-sisalla
  (s/and (s/cat :kohde ::pelkka-tr-paaluvali :kohteen-tiedot-alku ::kohteen-tieto :kohteen-tiedot-loppu ::kohteen-tieto)
         #(let [{:keys [kohde kohteen-tiedot-alku]} %
                paalupiste (dissoc kohde :tr-loppuosa :tr-loppuetaisyys)]
            (tr-paalupiste-tr-tiedon-mukainen? paalupiste kohteen-tiedot-alku))
         #(let [{:keys [kohde kohteen-tiedot-loppu]} %
                paalupiste (-> kohde
                               (dissoc :tr-alkuosa :tr-alkuetaisyys)
                               (clj-set/rename-keys {:tr-loppuosa :tr-alkuosa
                                                     :tr-loppuetaisyys :tr-alkuetaisyys}))]
            (tr-paalupiste-tr-tiedon-mukainen? paalupiste kohteen-tiedot-loppu))))

(defn oikean-muotoinen-tr
  ([tr] (oikean-muotoinen-tr tr ::tr))
  ([tr tr-spec]
   (s/explain-data tr-spec tr)))

(defn tr-valit-paallekkain?
  "Tetaa onko annetut tr-osoitteet päällekkäin. Jos kolmas argumentti on true, testaa onko tr-2 kokonaan tr-1 sisällä"
  ([tr-1 tr-2] (tr-valit-paallekkain? tr-1 tr-2 false))
  ([tr-1 tr-2 kokonaan-sisalla?]
   (let [tr-osoitteet [tr-1 tr-2]
         tr-spekista #(mapv (fn [spectulos]
                              (loop [[savain stulos] spectulos]
                                (if (or (nil? savain) (map? stulos))
                                  stulos
                                  (recur stulos))))
                            %)]
     (s/valid?
       (s/and
         ;; Ovathan molemmat valideja tr-osotteita
         (if kokonaan-sisalla?
           (s/tuple ::tr-vali ::tr)
           (s/tuple ::tr ::tr))
         ;; Sama tienumero, ajorata ja kaista?
         #(let [[tr-1 tr-2] (tr-spekista %)]
            (and (= (:tr-numero tr-1) (:tr-numero tr-2))
                 (= (:tr-ajorata tr-1) (:tr-ajorata tr-2))
                 (= (:tr-kaista tr-1) (:tr-kaista tr-2))))
         ;; Ovatko tierekisterit päällekkäin?
         #(let [[tr-1 tr-2 :as trt] (map (fn [tr]
                                           (with-meta tr
                                                      {:tyyppi (if (s/valid? ::tr-vali tr)
                                                                 :tr-vali
                                                                 :tr-piste)}))
                                         (tr-spekista %))]
            (case (count (filter (fn [tr]
                                   (-> tr meta :tyyppi (= :tr-piste)))
                                 trt))
              ;; molemmat tr-välejä
              0 (tr-paaluvali-tr-paaluvalin-sisalla? tr-1 tr-2 kokonaan-sisalla?)
              ;; toinen tr-piste
              1 (let [[tr-piste tr-vali] (sort-by ::tr-vali trt)]
                  (tr-paalupiste-tr-paaluvalin-sisalla? tr-piste tr-vali))
              ;; molemmat tr-pisteitä
              2 (= tr-1 tr-2))))
       tr-osoitteet))))

(defn validoi-paikka
  ([kohde kohteen-tiedot] (validoi-paikka kohde kohteen-tiedot true))
  ([kohde kohteen-tiedot paakohde?]
   (case (count kohteen-tiedot)
     ;; Onko kohde yhdellä osalla?
     1 (if paakohde?
         (s/explain-data ::paakohde-osan-sisalla [kohde (first kohteen-tiedot)])
         (s/explain-data ::alikohde-osion-sisalla [kohde (first kohteen-tiedot)]))
     ;; Onko kohde useammalla osalla?
     2 (if paakohde?
         (s/explain-data ::paakohde-osien-sisalla (->> kohteen-tiedot (sort-by (juxt :tr-numero :tr-osa)) (cons kohde) vec))
         (s/explain-data ::alikohde-osien-sisalla (->> kohteen-tiedot (sort-by (juxt :tr-numero :tr-osa)) (cons kohde) vec)))
     ;; Kohteen tietoja ei ole
     :else (s/explain-data ::kohteen-tiedot kohteen-tiedot))))

(defn validoi-kohde
  "Tarkistaa, että annettu pääkohde on validi. toiset-kohteet parametri voi sisältää myös 'muukohde' tyylisiä kohteita"
  ([kohde toiset-kohteet kohteen-tiedot] (validoi-kohde kohde toiset-kohteet kohteen-tiedot (pvm/vuosi (pvm/nyt))))
  ([kohde toiset-kohteet kohteen-tiedot vuosi]
   (let [tr-vali-spec (cond
                        (>= vuosi 2019) (s/and ::tr-paaluvali
                                               #(not (contains? % :ajorata))
                                               #(not (contains? % :kaista)))
                        (= vuosi 2018) ::tr-vali
                        (<= vuosi 2017) (s/and ::tr-paaluvali
                                               ::ajorata
                                               ::kaista))
         validoitu-muoto (oikean-muotoinen-tr kohde tr-vali-spec)
         validoitu-paallekkyys (filter #(tr-valit-paallekkain? kohde %)
                                       toiset-kohteet)
         validoitu-paikka (validoi-paikka kohde kohteen-tiedot)])))

(defn validoi-alikohde
  "Tarkistaa, että annettu alikohde on validi. toiset-alikohteet parametri sisältää kohteen muut alikohteet.
   Näiden kohteiden lisäksi se voi sisältää myös 'muukohde' tyylisiä kohteita."
  ([paakohde alikohde toiset-alikohteet alikohteen-tiedot] (validoi-alikohde paakohde alikohde toiset-alikohteet alikohteen-tiedot (pvm/vuosi (pvm/nyt))))
  ([paakohde alikohde toiset-alikohteet alikohteen-tiedot vuosi]
   (let [tr-vali-spec (cond
                        (>= vuosi 2019) (s/and ::tr-paaluvali
                                               ::ajorata
                                               ::kaista)
                        (= vuosi 2018) ::tr-vali
                        (<= vuosi 2017) ::tr-paaluvali)
         validoitu-muoto (oikean-muotoinen-tr alikohde tr-vali-spec)
         validoitu-alikohteidenpaallekkyys (filter #(tr-valit-paallekkain? alikohde %)
                                                   toiset-alikohteet)
         ;; Alikohteen tulee olla pääkohteen sisällä
         validoitu-paakohteenpaallekkyys (tr-valit-paallekkain? paakohde alikohde true)
         validoitu-paikka (validoi-paikka alikohde alikohteen-tiedot)])))

(defn validoi-muukohde
  "Tarkistaa, että annettu muukohde on validi. toiset-kohteet sisältää kaikki mahdolliset ali- ja muukohteet joita Harjassa on."
  ([paakohde muukohde toiset-kohteet kohteen-tiedot] (validoi-muukohde paakohde muukohde toiset-kohteet kohteen-tiedot (pvm/vuosi (pvm/nyt))))
  ([paakohde muukohde toiset-kohteet kohteen-tiedot vuosi]
   (let [tr-vali-spec (cond
                        (>= vuosi 2019) (s/and ::tr-paaluvali
                                               ::ajorata
                                               ::kaista)
                        (= vuosi 2018) ::tr-vali
                        (<= vuosi 2017) ::tr-paaluvali)
         validoitu-muoto (oikean-muotoinen-tr muukohde tr-vali-spec)
         validoitu-kohteiden-paallekkyys (filter #(tr-valit-paallekkain? muukohde %)
                                                 toiset-kohteet)
         ;; Muukohde ei saa olla pääkohteen sisällä tai sen kanssa päällekkäin
         validoitu-paakohteenpaallekkyys (tr-valit-paallekkain? paakohde muukohde)
         validoitu-paikka (validoi-paikka muukohde kohteen-tiedot)])))

(defn validoi-alustatoimenpide
  "Tarkistaa, että annettu kohde on oikean muotoinen."
  [alustatoimenpide kohde])

(defn validoi-alustatoimenpide-muukohde
  "Tarkistaa, että annettu kohde on oikean muotoinen."
  [alustatoimenpide kohde toiset-kohteet])

#?(:clj
   (defn tarkista-kohteen-ja-alikohteiden-sijannit
     "Tarkistaa, että annettu kohde on validi ja alikohteet ovat sen sen sisällä oikein."
     [kohde-id kohteen-sijainti alikohteet]

     (let [alikohteet (when alikohteet (sort-by (juxt #(get-in % [:sijainti :aosa]) #(get-in % [:sijainti :aet])) alikohteet))
           virheet (remove nil? (concat
                                  (validoi-sijainti kohteen-sijainti)
                                  (validoi-alikohteet kohde-id kohteen-sijainti alikohteet)))]
       (when (not (empty? virheet))
         (virheet/heita-poikkeus +kohteissa-viallisia-sijainteja+ virheet)))))

#?(:clj
   (defn validoi-alustatoimenpide [kohde-id kohteen-sijainti sijainti]
     (let [sijainti-virheet
           (when (not (alikohde-kohteen-sisalla? kohteen-sijainti sijainti))
             [(tee-virhe +viallinen-alustatoimenpiteen-sijainti+
                         (format "Alustatoimenpide ei ole kohteen (id: %s) sisällä." kohde-id))])
           puutteelliset-tiedot
           (when-not (and (tarkista-ajorata sijainti) (tarkista-kaista sijainti))
             [(tee-virhe +viallinen-alustatoimenpiteen-sijainti+
                         (str "Alustatoimenpiteeltä (" sijainti ") puuttuu "
                              (apply str
                                     (interpose ", " (keep (fn [{:keys [f nimi]}]
                                                             (when (nil? (f sijainti))
                                                               nimi))
                                                           [{:f tarkista-ajorata :nimi "ajorata"} {:f tarkista-kaista :nimi "kaista"}])))))])]
       (concat sijainti-virheet puutteelliset-tiedot))))

#?(:clj (defn tarkista-alustatoimenpiteiden-sijainnit
          "Varmistaa että kaikkien alustatoimenpiteiden sijainnit ovat kohteen sijainnin sisällä"
          [kohde-id kohteen-sijainti alustatoimet]
          (let [virheet
                (flatten
                  (keep (fn [{:keys [sijainti]}]
                          (let [kohteenvirheet
                                (concat
                                  (validoi-sijainti sijainti)
                                  (validoi-alustatoimenpide kohde-id kohteen-sijainti sijainti))]
                            kohteenvirheet))
                        alustatoimet))]
            (when (not (empty? virheet))
              (virheet/heita-poikkeus +kohteissa-viallisia-sijainteja+ virheet)))))

#?(:cljs
   (defn validoi-osan-maksimipituus [osan-pituus key pituus rivi]
     (when (integer? pituus)
       (let [osa (get rivi key)]
         (when-let [pit (get osan-pituus osa)]
           (when (> pituus pit)
             (str "Osan " osa " maksimietäisyys on " pit)))))))

#?(:cljs
   (defn validoi-yllapitokohteen-osoite
     [osan-pituudet-teille kentta {:keys [tr-numero tr-alkuosa tr-alkuetaisyys
                                          tr-loppuosa tr-loppuetaisyys] :as kohde}]
     (when osan-pituudet-teille
       (let [osan-pituudet (osan-pituudet-teille tr-numero)]
         (or
           (cond
             (and (= kentta :tr-alkuosa) (not (contains? osan-pituudet tr-alkuosa)))
             (str "Tiellä " tr-numero " ei ole osaa " tr-alkuosa)

             (and (= kentta :tr-loppuosa) (not (contains? osan-pituudet tr-loppuosa)))
             (str "Tiellä " tr-numero " ei ole osaa " tr-loppuosa))

           (when (= kentta :tr-alkuetaisyys)
             (validoi-osan-maksimipituus osan-pituudet :tr-alkuosa tr-alkuetaisyys kohde))

           (when (= kentta :tr-loppuetaisyys)
             (validoi-osan-maksimipituus osan-pituudet :tr-loppuosa tr-loppuetaisyys kohde)))))))

#?(:cljs
   (defn alkuosa-ei-lopun-jalkeen [aosa {losa :tr-loppuosa}]
     (when (and aosa losa (> aosa losa))
       "Al\u00ADku\u00ADo\u00ADsa ei voi olla lop\u00ADpu\u00ADo\u00ADsan jäl\u00ADkeen")))

#?(:cljs
   (defn alkuetaisyys-ei-lopun-jalkeen [alkuet {aosa :tr-alkuosa
                                                losa :tr-loppuosa
                                                loppuet :tr-loppuetaisyys}]
     (when (and aosa losa alkuet loppuet
                (= aosa losa)
                (> alkuet loppuet))
       "Alku\u00ADe\u00ADtäi\u00ADsyys ei voi olla lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyy\u00ADden jäl\u00ADkeen")))

#?(:cljs
   (defn loppuosa-ei-alkua-ennen [losa {aosa :tr-alkuosa}]
     (when (and aosa losa (< losa aosa))
       "Lop\u00ADpu\u00ADosa ei voi olla al\u00ADku\u00ADo\u00ADsaa ennen")))

#?(:cljs
   (defn loppuetaisyys-ei-alkua-ennen [loppuet {aosa :tr-alkuosa
                                                losa :tr-loppuosa
                                                alkuet :tr-alkuetaisyys}]
     (when (and aosa losa alkuet loppuet
                (= aosa losa)
                (< loppuet alkuet))
       "Lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys ei voi olla enn\u00ADen al\u00ADku\u00ADe\u00ADtäi\u00ADsyyt\u00ADtä")))

#?(:clj
   (defn yllapitokohteen-tarkka-tila [yllapitokohde]
     (cond
       (and (:kohde-valmispvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:kohde-valmispvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :kohde-valmis

       (and (:tiemerkinta-loppupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:tiemerkinta-loppupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :tiemerkinta-valmis

       (and (:tiemerkinta-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:tiemerkinta-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :tiemerkinta-aloitettu

       (and (:paallystys-loppupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paallystys-loppupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paallystys-valmis

       (and (:paallystys-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paallystys-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paallystys-aloitettu

       (and (:paikkaus-loppupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paikkaus-loppupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paikkaus-valmis

       (and (:paikkaus-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paikkaus-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paikkaus-aloitettu

       (and (:kohde-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:kohde-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :kohde-aloitettu

       :default
       :ei-aloitettu)))

(defn kuvaile-kohteen-tila [tila]
  (case tila
    :ei-aloitettu "Ei aloitettu"
    :kohde-aloitettu "Kohde aloitettu"
    :paallystys-aloitettu "Päällystys aloitettu"
    :paallystys-valmis "Päällystys valmis"
    :paikkaus-aloitettu "Paikkaus aloitettu"
    :paikkaus-valmis "Paikkaus valmis"
    :tiemerkinta-aloitettu "Tiemerkintä aloitettu"
    :tiemerkinta-valmis "Tiemerkintä valmis"
    :kohde-valmis "Kohde valmis"
    "Ei tiedossa"))

(defn yllapitokohteen-tila-kartalla [tarkka-tila]
  (case tarkka-tila
    :ei-aloitettu :ei-aloitettu
    :kohde-aloitettu :kesken
    :paallystys-aloitettu :kesken
    :paallystys-valmis :kesken
    :paikkaus-aloitettu :kesken
    :paikkaus-valmis :kesken
    :tiemerkinta-aloitettu :kesken
    :tiemerkinta-valmis :valmis
    :kohde-valmis :valmis))

(defn kuvaile-kohteen-tila-kartalla [tila]
  (case tila
    :valmis "Valmis"
    :kesken "Kesken"
    :ei-aloitettu "Ei aloitettu"
    "Ei tiedossa"))

(def yllapitokohde-kartalle-xf
  ;; Ylläpitokohde näytetään kartalla 'kohdeosina'.
  ;; Tämä transducer olettaa saavansa vectorin ylläpitokohteita ja palauttaa
  ;; ylläpitokohteiden kohdeosat valmiina näytettäväksi kartalle.
  ;; Palautuneilla kohdeosilla on pääkohteen tiedot :yllapitokohde avaimen takana.
  (comp
    (mapcat (fn [kohde]
              (keep (fn [kohdeosa]
                      (assoc kohdeosa :yllapitokohde (dissoc kohde :kohdeosat)
                                      :tyyppi-kartalla (:yllapitokohdetyotyyppi kohde)
                                      :tila (:tila kohde)
                                      :yllapitokohde-id (:id kohde)))
                    (:kohdeosat kohde))))
    (keep #(and (:sijainti %) %))))

(defn yllapitokohteen-kokonaishinta [{:keys [sopimuksen-mukaiset-tyot maaramuutokset toteutunut-hinta
                                             bitumi-indeksi arvonvahennykset kaasuindeksi sakot-ja-bonukset]}]
  (reduce + 0 (remove nil? [sopimuksen-mukaiset-tyot        ;; Sama kuin kohteen tarjoushinta
                            maaramuutokset                  ;; Kohteen määrämuutokset summattuna valmiiksi yhteen
                            arvonvahennykset                ;; Sama kuin arvonmuutokset
                            sakot-ja-bonukset               ;; Sakot ja bonukset summattuna valmiiksi yhteen.
                            ;; HUOM. sillä oletuksella, että sakot ovat miinusta ja bonukset plussaa.
                            bitumi-indeksi
                            kaasuindeksi
                            toteutunut-hinta                ;; Kohteen toteutunut hinta (vain paikkauskohteilla)
                            ])))

(defn yllapitokohde-tekstina
  "Näyttää ylläpitokohteen kohdenumeron ja nimen.

  Optiot on map, jossa voi olla arvot:
  osoite              Kohteen tierekisteriosoite.
                      Näytetään sulkeissa kohteen tietojen perässä sulkeissa, jos löytyy."
  ([kohde] (yllapitokohde-tekstina kohde {}))
  ([kohde optiot]
   (let [kohdenumero (or (:kohdenumero kohde) (:numero kohde) (:yllapitokohdenumero kohde))
         nimi (or (:nimi kohde) (:yllapitokohdenimi kohde))
         osoite (when-let [osoite (:osoite optiot)]
                  (let [tr-osoite (tr-domain/tierekisteriosoite-tekstina osoite {:teksti-ei-tr-osoitetta? false
                                                                                 :teksti-tie? false})]
                    (when-not (empty? tr-osoite)
                      (str " (" tr-osoite ")"))))]
     (str kohdenumero " " nimi osoite))))

(defn lihavoi-vasta-muokatut [rivit]
  (let [viikko-sitten (pvm/paivaa-sitten 7)]
    (map (fn [{:keys [muokattu aikataulu-muokattu] :as rivi}]
           (assoc rivi :lihavoi
                       (or (and muokattu (pvm/ennen? viikko-sitten muokattu))
                           (and aikataulu-muokattu (pvm/ennen? viikko-sitten aikataulu-muokattu)))))
         rivit)))

(def tarkan-aikataulun-toimenpiteet [:murskeenlisays :ojankaivuu :rp_tyot :rumpujen_vaihto :sekoitusjyrsinta :muu])
(def tarkan-aikataulun-toimenpide-fmt
  {:ojankaivuu "Ojankaivuu"
   :rp_tyot "RP-työt"
   :rumpujen_vaihto "Rumpujen vaihto"
   :sekoitusjyrsinta "Sekoitusjyrsintä"
   :murskeenlisays "Murskeenlisäys"
   :muu "Muu"})