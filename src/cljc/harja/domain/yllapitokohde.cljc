(ns harja.domain.yllapitokohde
  "Ylläpitokohteiden yhteisiä apureita"
  (:require
    [harja.tyokalut.spec-apurit :as spec-apurit]
    [clojure.string :as str]
    [harja.domain.tierekisteri :as tr-domain]
    [clojure.spec.alpha :as s]
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

(defn oikean-muotoinen-tr
  "Tarkistaa, että annetussa tr-osoitteessa on tien numero, aosa, aet, losa ja let annettuna.
   Niiden tulee olla myös oikein järjestetty."
  [{:keys [tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys] :as tr}]
  (cond-> {}
          ;; Tarkistetaan, että arvot on annettu
          (not (and tr-numero (integer? tr-numero))) (update :tr-numero assoc :vaara-muotoinen tr-numero)
          (not (and tr-alkuosa (integer? tr-alkuosa))) (update :tr-alkuosa assoc :vaara-muotoinen tr-alkuosa)
          (not (and tr-alkuetaisyys (integer? tr-alkuetaisyys))) (update :tr-alkuetaisyys assoc :vaara-muotoinen tr-alkuetaisyys)
          (not (and tr-loppuosa (integer? tr-loppuosa))) (update :tr-loppuosa assoc :vaara-muotoinen tr-loppuosa)
          (not (and tr-loppuetaisyys (integer? tr-loppuetaisyys))) (update :tr-loppuetaisyys assoc :vaara-muotoinen tr-loppuetaisyys)
          ;; Tarkistetaan, että ne on oikein
          (not (or (losa>aosa? tr)
                   (losa=aosa? tr))) (-> (update :tr-alkuosa assoc :liian-iso tr-alkuosa)
                                         (update :tr-loppuosa assoc :liian-pieni tr-loppuosa))
          (and (losa=aosa? tr)
               (not (or (let>aet? tr)
                        (let=aet? tr)))) (-> (update :tr-alkuetaisyys assoc :liian-iso tr-alkuetaisyys)
                                             (update :tr-loppuetaisyys assoc :liian-pieni tr-loppuetaisyys))))

(defn validoi-kohde
  "Tarkistaa, että annettu kohde on oikean muotoinen."
  [kohde toiset-kohteet muut-alikohteet])

(defn validoi-alikohde
  "Tarkistaa, että annettu kohde on oikean muotoinen."
  [alikohde toiset-alikohteet kohde])

(defn validoi-muukohde
  "Tarkistaa, että annettu kohde on oikean muotoinen."
  [muu-kohde kohde toiset-kohteet])

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
  (reduce + 0 (remove nil? [sopimuksen-mukaiset-tyot ;; Sama kuin kohteen tarjoushinta
                            maaramuutokset ;; Kohteen määrämuutokset summattuna valmiiksi yhteen
                            arvonvahennykset ;; Sama kuin arvonmuutokset
                            sakot-ja-bonukset ;; Sakot ja bonukset summattuna valmiiksi yhteen.
                            ;; HUOM. sillä oletuksella, että sakot ovat miinusta ja bonukset plussaa.
                            bitumi-indeksi
                            kaasuindeksi
                            toteutunut-hinta ;; Kohteen toteutunut hinta (vain paikkauskohteilla)
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