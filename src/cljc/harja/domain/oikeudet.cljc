(ns harja.domain.oikeudet
  "Rajapinta oikeustarkistuksiin"
  (:require
   #?(:clj [harja.domain.oikeudet.makrot :refer [maarittele-oikeudet!]])
   [harja.domain.roolit :as roolit]
   #?(:clj [slingshot.slingshot :refer [throw+]])
   #?(:cljs [harja.tiedot.istunto :as istunto])
   [clojure.set :as s]
   [taoensso.timbre :as log])
  #?(:cljs
     (:require-macros [harja.domain.oikeudet.makrot :refer [maarittele-oikeudet!]])))

(declare on-oikeus? on-muu-oikeus?)
(defrecord KayttoOikeus [kuvaus roolien-oikeudet])

#?(:clj
   (def ^:dynamic *oikeustarkistus-tehty*
     "Onko tämän pyynnön käsittelyssä tehty jokin oikeustarkistus?
  Pyynnön käsittelyn aikana bindattu atomiin, jonka alkuarvo on false.
  Arvo on nil, jos ei ole pyyntöä käsittelemässä."
     nil))

#?(:clj
   (defn merkitse-oikeustarkistus-tehdyksi! []
     (when *oikeustarkistus-tehty*
       (reset! *oikeustarkistus-tehty* true))))

#?(:clj
   (def ei-oikeustarkistusta! merkitse-oikeustarkistus-tehdyksi!))

#?(:cljs
   (extend-type KayttoOikeus
     cljs.core/IFn
     (-invoke
       ([this]
        (or (on-oikeus? :luku this nil @istunto/kayttaja)
            (on-oikeus? :kirjoitus this nil @istunto/kayttaja)))
       ([this urakka-id]
        (or (on-oikeus? :luku this urakka-id @istunto/kayttaja)
            (on-oikeus? :kirjoitus this urakka-id @istunto/kayttaja)))
       ([this urakka-id muu-oikeustyyppi]
        (on-muu-oikeus? muu-oikeustyyppi this urakka-id @istunto/kayttaja)))))

(maarittele-oikeudet!)

(defn- roolin-oikeudet [kayttooikeus rooli]
  (or (get (:roolien-oikeudet kayttooikeus) rooli)
      #{}))

(defn- on-lukuoikeus? [kayttooikeus rooli]
  (let [oikeudet (roolin-oikeudet kayttooikeus rooli)]
    (cond
      (oikeudet "R*") :kaikki
      (oikeudet "R+") :organisaatio
      (oikeudet "R") :urakka)))

(defn- on-kirjoitusoikeus? [kayttooikeus rooli]
  (let [oikeudet (roolin-oikeudet kayttooikeus rooli)]
    (cond
      (oikeudet "W*") :kaikki
      (oikeudet "W+") :organisaatio
      (oikeudet "W") :urakka)))

(defn- on-nimetty-oikeus? [nimi kayttooikeus rooli]
  (let [oikeudet (roolin-oikeudet kayttooikeus rooli)]
    (cond
      (oikeudet (str nimi "*")) :kaikki
      (oikeudet (str nimi "+")) :organisaatio
      (oikeudet nimi) :urakka)))

(defn- on-oikeus-urakkaan? [oikeus-pred urakka-id organisaatio-id organisaation-urakka?
                            {rooli :rooli
                             roolin-urakka-id :urakka-id
                             roolin-organisaatio-id :organisaatio-id}]
  (let [konteksti (oikeus-pred rooli)
        urakkaoikeus? (= roolin-urakka-id urakka-id)]
    (case konteksti
      ;; Kaikki antaa oikeuden mihin vaan urakkaan
      :kaikki :kaikki

      ;; Organisaatio, jos testattava urakka kuuluu organisaatiolle.
      ;; Jos rooli on annettu organisaatiokohtaisesti, tarkistetaan, että käyttäjän
      ;; organisaatio on sama kuin roolin organisaatio.
      ;; Erikoistapauksena urakkarooli voi olla myös muusta organisaatiosta
      :organisaatio (if (and (or (nil? roolin-organisaatio-id)
                                 (= organisaatio-id roolin-organisaatio-id))
                             organisaation-urakka?)
                      :organisaatio
                      (when urakkaoikeus?
                        :urakka))

      ;; Urakka, jos urakkarooli on annettu testattavalle urakalle
      :urakka (when urakkaoikeus?
                :urakka)

      ;; Ei oikeutta
      nil)))

(defn- on-oikeus?
  "Tarkistaa :luku, :kirjoitus tai muun tyyppisen oikeuden"
  [tyyppi oikeus urakka-id {:keys [organisaation-urakat roolit organisaatio
                                   urakkaroolit organisaatioroolit] :as kayttaja}]
  (let [urakka-id (or (:urakka urakka-id) urakka-id) ;; HAR-6409 hotfix
        ]
    (when-not (or (nil? urakka-id) (number? urakka-id))
     (#?(:clj  log/error
         :cljs log/debug)
       "KRIITTINEN BUGI OIKEUSTARKASTUKSESSA: Urakka-id:n täytyy olla joko nil tai numero " (pr-str urakka-id)))
    (when-not (instance? KayttoOikeus oikeus)
      (#?(:clj  log/error
          :cljs log/debug)
        "KRIITTINEN BUGI OIKEUSTARKASTUKSESSA: Annettu oikeus ei ole KayttoOikeus " (pr-str oikeus)))
    (when-not (every? #(contains? kayttaja %) [:organisaation-urakat :roolit :organisaatio
                                               :urakkaroolit :organisaatioroolit])
      (#?(:clj  log/error
          :cljs log/debug)
        "KRIITTINEN BUGI OIKEUSTARKASTUKSESSA: Käyttäjältä puuttuu jokin avaimista "
        (pr-str [:organisaation-urakat :roolit :organisaatio :urakkaroolit :organisaatioroolit])
        " "
        (pr-str kayttaja)))
    (let [oikeus-pred (partial (case tyyppi
                                 :luku on-lukuoikeus?
                                 :kirjoitus on-kirjoitusoikeus?
                                 (partial on-nimetty-oikeus? tyyppi))
                               oikeus)
          kaikki-urakkaroolit (apply concat (vals urakkaroolit))
          kaikki-organisaatioroolit (apply concat (vals organisaatioroolit))
          kaikki-roolit (concat roolit
                                kaikki-urakkaroolit
                                kaikki-organisaatioroolit)]

      (or (if-not urakka-id
            ;; Jos urakkaa ei annettu, tarkista että rooli on jossain
            (some oikeus-pred kaikki-roolit)


            ;; Jos urakka on annettu, tarkista että on oikeus tässä urakassa
            ;; tai oikeus, joka implikoi urakan (+ = org. urakka, * = mikä tahansa urakka)
            (some #(on-oikeus-urakkaan? oikeus-pred urakka-id (:id organisaatio)
                                        (organisaation-urakat urakka-id) %)
                  (concat
                    ;; Yleiset roolit (ei urakkaan sidottu)
                    (map (fn [r]
                           {:rooli r :urakka-id nil}) roolit)
                    ;; Urakkakohtaiset roolit urakan id:llä
                    (mapcat (fn [[urakka-id roolit]]
                              (for [r roolit]
                                {:rooli r :urakka-id urakka-id}))
                            urakkaroolit)
                    ;; Organisaatiokohtaiset roolit organisation id:llä
                    (mapcat (fn [[org-id roolit]]
                              (for [r roolit]
                                {:rooli r :organisaatio-id org-id}))
                            organisaatioroolit))))
          false))))

(defn on-muu-oikeus?
  "Tarkistaa määritellyn muun (kuin :luku tai :kirjoitus) oikeustyypin"
  #?(:cljs
     ([tyyppi oikeus urakka-id]
      (on-muu-oikeus? tyyppi oikeus urakka-id @istunto/kayttaja)))
  ([tyyppi oikeus urakka-id kayttaja]
   (on-oikeus? tyyppi oikeus urakka-id kayttaja)))

(defn voi-lukea?
  #?(:cljs
     ([oikeus]
      (voi-lukea? oikeus nil @istunto/kayttaja)))
  #?(:cljs
     ([oikeus urakka-id]
      (voi-lukea? oikeus urakka-id @istunto/kayttaja)))
  ([oikeus urakka-id kayttaja]
   (on-oikeus? :luku oikeus urakka-id kayttaja)))

(defn voi-kirjoittaa?
  #?(:cljs
     ([oikeus]
      (voi-kirjoittaa? oikeus nil @istunto/kayttaja)))
  #?(:cljs
     ([oikeus urakka-id]
      (voi-kirjoittaa? oikeus urakka-id @istunto/kayttaja)))
  ([oikeus urakka-id kayttaja]
   (on-oikeus? :kirjoitus oikeus urakka-id kayttaja)))

#?(:clj
   (defn vaadi-lukuoikeus
     ([oikeus kayttaja]
      (vaadi-lukuoikeus oikeus kayttaja nil))
     ([oikeus kayttaja urakka-id]
      (merkitse-oikeustarkistus-tehdyksi!)
      (when-not (voi-lukea? oikeus urakka-id kayttaja)
        (throw+ (roolit/->EiOikeutta
                 (str "Käyttäjällä '" (pr-str kayttaja) "' ei lukuoikeutta "
                      (:kuvaus oikeus)
                      (when urakka-id
                        (str " urakassa " urakka-id)))))))))

#?(:clj
   (defn vaadi-kirjoitusoikeus
     ([oikeus kayttaja]
      (vaadi-kirjoitusoikeus oikeus kayttaja nil))
     ([oikeus kayttaja urakka-id]
      (merkitse-oikeustarkistus-tehdyksi!)
      (when-not (voi-kirjoittaa? oikeus urakka-id kayttaja)
        (throw+ (roolit/->EiOikeutta
                 (str "Käyttäjällä '" (pr-str kayttaja) "' ei kirjoitusoikeutta "
                      (:kuvaus oikeus)
                      (when urakka-id
                        (str " urakassa " urakka-id)))))))))
#?(:clj
   (defn vaadi-oikeus
     ([tyyppi oikeus kayttaja]
      (vaadi-oikeus tyyppi oikeus kayttaja nil))
     ([tyyppi oikeus kayttaja urakka-id]
      (merkitse-oikeustarkistus-tehdyksi!)
      (when-not  (on-muu-oikeus? tyyppi oikeus urakka-id kayttaja)
        (throw+ (roolit/->EiOikeutta
                 (str "Käyttäjällä '" (pr-str kayttaja) "' ei oikeutta '" tyyppi "' "
                      (:kuvaus oikeus)
                      (when urakka-id
                        (str " urakassa " urakka-id)))))))))

#?(:clj
   (defn voi-kirjata-ls-tyokalulla?
     [kayttaja urakka-id]
     (or
       (voi-kirjoittaa?
         urakat-laadunseuranta-tarkastukset
         urakka-id kayttaja)
       (voi-kirjoittaa?
         laadunseuranta-kirjaus
         urakka-id kayttaja))))
#?(:clj
   (defn vaadi-ls-tyokalun-kirjausoikeus
     [kayttaja urakka-id]
     (when-not (voi-kirjata-ls-tyokalulla? kayttaja urakka-id)
       (throw+ (roolit/->EiOikeutta
                 (str "Käyttäjällä '" (pr-str kayttaja) "' ei oikeutta tehdä tarkastustyökalulla kirjauksia "
                      (when urakka-id
                        (str " urakassa " urakka-id))))))))

(def ilmoitus-ei-oikeutta-muokata-toteumaa
  "Käyttäjäroolillasi ei ole oikeutta muokata tätä toteumaa.")

(defn roolin-kuvaus [rooli]
  (:kuvaus (get roolit rooli)))

(defn kayttajan-urakat [{urakkaroolit :urakkaroolit}]
  #?(:clj (merkitse-oikeustarkistus-tehdyksi!))
  (into #{} (keys urakkaroolit)))

(defn oikeuden-puute-kuvaus [oikeustyyppi oikeus]
  (str "Käyttäjärooleissasi ei ole "
       (case oikeustyyppi
         :kirjoitus "kirjoitusoikeutta"
         :luku "lukuoikeutta"
         (str "\"" oikeustyyppi "\" oikeutta"))
       ": "
       (:kuvaus oikeus)))

(defn tarkistettava-oikeus-kok-hint-tyot
  [urakkatyyppi]
  (if (= urakkatyyppi :tiemerkinta)
    urakat-toteutus-kokonaishintaisettyot
    urakat-suunnittelu-kokonaishintaisettyot))
