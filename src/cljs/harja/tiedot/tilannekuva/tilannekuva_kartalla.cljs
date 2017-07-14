(ns harja.tiedot.tilannekuva.tilannekuva-kartalla
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.domain.tilannekuva :as domain]
            [harja.atom :refer-macros [reaction<!]
             :refer [paivita-periodisesti]]
            [harja.ui.kartta.esitettavat-asiat
             :as esitettavat-asiat
             :refer [kartalla-esitettavaan-muotoon]]
            [harja.ui.openlayers :as openlayers]
            [clojure.string :as str]
            [harja.geo :as geo]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.kartta.apurit :refer [+koko-suomi-extent+]]
            [harja.tyokalut.functor :refer [fmap]])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce karttataso-tilannekuva (atom false))
(defonce haetut-asiat (atom nil))
(defonce valittu-tila (atom nil)) ; :nykytilanne | :historiakuva | :tienakyma

(defonce url-hakuparametrit (atom nil))
(defonce tilannekuvan-asiat-kartalla (atom {}))
(defonce tilannekuvan-organisaatiot (atom []))


(def lisaa-karttatyyppi-fn
  {:ilmoitukset            #(assoc % :tyyppi-kartalla (:ilmoitustyyppi %))
   :turvallisuuspoikkeamat #(assoc % :tyyppi-kartalla :turvallisuuspoikkeama)
   :tarkastukset           #(assoc % :tyyppi-kartalla :tarkastus)
   :laatupoikkeamat        #(assoc % :tyyppi-kartalla :laatupoikkeama)
   :paikkaus               #(assoc % :tyyppi-kartalla :paikkaus)
   :paallystys             #(assoc % :tyyppi-kartalla :paallystys)
   :toteumat               #(assoc % :tyyppi-kartalla :toteuma)
   :tietyomaat    #(assoc % :tyyppi-kartalla :tietyomaa)
   :tietyoilmoitukset #(assoc % :tyyppi-kartalla :tietyoilmoitus)
   :varustetoteumat identity})

(def ^{:doc "Mäpätään tilannekuvan tasojen nimet :tilannekuva- etuliitteelle,
etteivät ne mene päällekkäin muiden tasojen kanssa."}
  karttatason-nimi
  {:ilmoitukset            :tilannekuva-ilmoitukset
   :turvallisuuspoikkeamat :tilannekuva-turvallisuuspoikkeamat
   :tarkastukset           :tilannekuva-tarkastukset
   :laatupoikkeamat        :tilannekuva-laatupoikkeamat
   :paikkaus               :tilannekuva-paikkaus
   :paallystys             :tilannekuva-paallystys
   :tyokoneet              :tilannekuva-tyokoneet
   :toteumat               :tilannekuva-toteumat
   :tietyomaat    :tilannekuva-tietyomaat
   :tietyoilmoitukset :tilannekuva-tietyoilmoitukset
   :varustetoteumat :tilannekuva-varustetoteumat})

(defmulti muodosta-karttataso (fn [taso uudet-asiat] taso))

(defmethod muodosta-karttataso :default [taso uudet-asiat]
  (kartalla-esitettavaan-muotoon
   uudet-asiat
   (constantly false)
   (map (lisaa-karttatyyppi-fn taso))))

(defn- muodosta-kuva-karttataso
  ([nimi selitteet hakuparametrit] (muodosta-kuva-karttataso nimi selitteet hakuparametrit nil))
  ([nimi selitteet hakuparametrit indikaattori]
   (openlayers/luo-kuvataso
    nimi
    selitteet
    "tk" hakuparametrit
    "ind" (when-not (= :historiakuva @valittu-tila)
            ;; Historiakuvassa ei haluta indikaattoria
            (str indikaattori)))))

(defmethod muodosta-karttataso :toteumat [taso toimenpiteet]
  (let [yhteensa (reduce + (map :lukumaara toimenpiteet))]
    (muodosta-kuva-karttataso
     :tilannekuva-toteumat
     (into #{}
           (map (comp esitettavat-asiat/toimenpiteen-selite :toimenpide))
           toimenpiteet)
     @url-hakuparametrit
     yhteensa)))

(defmethod muodosta-karttataso :tarkastukset [taso tarkastukset]
  (muodosta-kuva-karttataso
   :tilannekuva-tarkastukset esitettavat-asiat/tarkastus-selitteet
   @url-hakuparametrit))

(defmethod muodosta-karttataso :tyokoneet [taso {:keys [tehtavat viimeisin]}]
  (muodosta-kuva-karttataso
   :tilannekuva-tyokoneet (fmap esitettavat-asiat/tyokoneen-selite tehtavat)
   @url-hakuparametrit
   viimeisin))

(def kuvataso? #{:tarkastukset :toteumat})

(defn- yhdista-uudet-tasot [vanhat-tasot uudet-tasot]
  (reduce-kv (fn [nykyiset-tasot nimi taso]
               (let [nykyinen-taso (get nykyiset-tasot nimi)]
                 ;; Ei korvata tasoa, jos se on sama kuvataso (jottai ei
                 ;; menetetä jo ladattuja tilejä eikä tule flickeriä)
                 (if (openlayers/sama-kuvataso? nykyinen-taso taso)
                   nykyiset-tasot
                   (assoc nykyiset-tasot nimi taso))))
             vanhat-tasot
             uudet-tasot))

;; Päivittää tilannekuvan karttatasot kun niiden tiedot ovat muuttuneet.
;; Muuntaa kartalla esitettävään muotoon ne tasot, joiden tiedot on oikeasti
;; muuttuneet.
(defn paivita-tilannekuvatasot
  "Päivittää tilannekuvan karttatasot kun niiden tiedot haetuissa asioissa
  ovat muuttuneet. Ottaa sisään haettujen asioiden vanhan ja uuden version."
  [vanha uusi]
  (if (nil? uusi)
    ;; Jos tilannekuva poistuu näkyvistä, haetut-asiat on nil
    (reset! tilannekuvan-asiat-kartalla {})

    ;; Päivitä kaikki eri tyyppiset asiat
    (let [tasot (into #{} (concat (keys uusi) (keys vanha)))]
      (loop [uudet-tasot {}
             [taso & tasot] (seq tasot)]
        (if-not taso
          (swap! tilannekuvan-asiat-kartalla yhdista-uudet-tasot uudet-tasot)
          (let [vanhat-asiat (get vanha taso)
                uudet-asiat (get uusi taso)
                tason-nimi (karttatason-nimi taso)]
            (recur (cond
                     ;; Jos taso on nyt tyhjä, poistetaan se
                     ;; (nil taso poistuu kartalta)
                     (empty? uudet-asiat)
                     (assoc uudet-tasot tason-nimi nil)

                     ;; Jos tason asiat ovat muuttuneet, muodostetaan
                     ;; kartalla esitettävä muoto
                     (or (not= vanhat-asiat uudet-asiat)
                         (kuvataso? taso))
                     (assoc uudet-tasot
                            tason-nimi (muodosta-karttataso taso uudet-asiat))

                     :default
                     uudet-tasot)
                   tasot)))))))

(add-watch haetut-asiat :paivita-tilannekuvatasot
           (fn [_ _ vanha uusi] (paivita-tilannekuvatasot vanha uusi)))

(defn- organisaation-geometria [piirrettava]
  (let [alue (:alue piirrettava)]
    (when (map? alue)
      (assoc (update-in piirrettava
                  [:alue]
                  assoc
                  :fill false
                  :stroke {:width 2}
                  :z-index 1)
        :type :ur
        :nimi nil))))

(defn zoomaa-urakoihin! [urakat]
  (reset! nav/kartan-extent
          (if-not (empty? urakat)
            (-> (geo/extent-monelle (map :alue urakat))
                (geo/laajenna-extent geo/pisteen-extent-laajennus))

            +koko-suomi-extent+)))

(defn aseta-valitut-organisaatiot! [suodattimet]
  (reset! tilannekuvan-organisaatiot (into []
                                           (keep organisaation-geometria)
                                           (domain/valitut-kentat suodattimet))))

(defn seuraa-alueita! [suodattimet]
  (zoomaa-urakoihin! (aseta-valitut-organisaatiot! (:alueet @suodattimet)))
  (add-watch suodattimet ::alueen-seuraus (fn [_ _ vanha-tila uusi-tila]
                                             (when-not (= (domain/valitut-suodattimet (:alueet vanha-tila))
                                                          (domain/valitut-suodattimet (:alueet uusi-tila)))
                                               (zoomaa-urakoihin! (aseta-valitut-organisaatiot! (:alueet uusi-tila)))))))

(defn lopeta-alueen-seuraus! [suodattimet]
  (remove-watch suodattimet ::alueen-seuraus))
