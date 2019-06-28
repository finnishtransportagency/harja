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
            [harja.geo :as geo]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.kartta.apurit :refer [+koko-suomi-extent+]]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.tiedot.tieluvat.tieluvat-kartalla :as tieluvat-kartalla])

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
   :toteumat               #(assoc % :tyyppi-kartalla :toteuma)
   :tietyomaat    #(assoc % :tyyppi-kartalla :tietyomaa)
   :tietyoilmoitukset #(assoc % :tyyppi-kartalla :tietyoilmoitus)
   :tieluvat #(assoc % :tyyppi-kartalla :tielupa)
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
   :tieluvat :tilanneluva-tieluvat
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

(defmethod muodosta-karttataso :tieluvat [taso uudet-asiat]
  (kartalla-esitettavaan-muotoon
    (tieluvat-kartalla/hajota-tieluvat uudet-asiat)
    (constantly false)
    (map (lisaa-karttatyyppi-fn taso))))

(defmethod muodosta-karttataso :toteumat [taso tason-tiedot]
  (let [indikaattori (:viimeisin-toteuma tason-tiedot)]
    (muodosta-kuva-karttataso
     :tilannekuva-toteumat
     (into #{}
           (map (comp esitettavat-asiat/toimenpiteen-selite :toimenpide))
           (:selitteet tason-tiedot))
     @url-hakuparametrit
     indikaattori)))

(defmethod muodosta-karttataso :tarkastukset [taso tarkastukset]
  (muodosta-kuva-karttataso
   :tilannekuva-tarkastukset esitettavat-asiat/tarkastus-selitteet-reiteille
   @url-hakuparametrit))

(defmethod muodosta-karttataso :tyokoneet [taso {:keys [tehtavat viimeisin]}]
  (muodosta-kuva-karttataso
   :tilannekuva-tyokoneet (fmap esitettavat-asiat/tyokoneen-selite tehtavat)
   @url-hakuparametrit
   viimeisin))

(defmethod muodosta-karttataso :paallystys [taso [ind]]
  (muodosta-kuva-karttataso
   :tilannekuva-paallystys esitettavat-asiat/paallystys-selitteet
   @url-hakuparametrit
   ind))

(def kuvataso? #{:tarkastukset :toteumat :paallystys})

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
                         ;; PENDING: Tätä ei varmaan tarvittaisi, koska tason indikaattori
                         ;; muuttuu kun tasolla tapahtuu relevantti muutos.
                         ;; Toisaalta kuvat tulevat selaimen cachesta joka tapauksessa
                         ;; jos indikaattoriarvo on sama.
                         (kuvataso? taso))
                     (assoc uudet-tasot
                            tason-nimi (muodosta-karttataso taso uudet-asiat))

                     :default
                     uudet-tasot)
                   tasot)))))))

(add-watch haetut-asiat :paivita-tilannekuvatasot
           (fn [_ _ vanha uusi] (paivita-tilannekuvatasot vanha uusi)))

(defn- organisaation-geometria [piirrettava {stroke :stroke}]
  (let [alue (:alue piirrettava)]
    (when (map? alue)
      (assoc (update-in piirrettava
                  [:alue]
                  assoc
                  :fill false
                  :stroke (merge {:width 2}
                                 stroke)
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
  (let [urakkatyypit-ilman-rajoja #{:paallystys}
        urakkatyypit-rajoineen (apply dissoc suodattimet urakkatyypit-ilman-rajoja)
        urakkatyypit-rajoitta (select-keys suodattimet urakkatyypit-ilman-rajoja)]
    (reset! tilannekuvan-organisaatiot (vec (concat (keep #(organisaation-geometria % {})
                                                          (domain/valitut-kentat urakkatyypit-rajoineen))
                                                    (keep #(organisaation-geometria % {:stroke {:color (str "rgb(0, 0, 0, 0)")}})
                                                          (domain/valitut-kentat urakkatyypit-rajoitta)))))))

(defn seuraa-alueita! [{:keys [aluesuodattimet] :as suodattimet}]
  (zoomaa-urakoihin! (aseta-valitut-organisaatiot! aluesuodattimet))
  (add-watch suodattimet ::alueen-seuraus (fn [_ _ {vanha-tila :aluesuodattimet} {uusi-tila :aluesuodattimet}]
                                             (when-not (= (domain/valitut-suodattimet vanha-tila)
                                                          (domain/valitut-suodattimet uusi-tila))
                                               (zoomaa-urakoihin! (aseta-valitut-organisaatiot! uusi-tila))))))

(defn lopeta-alueen-seuraus! [suodattimet]
  (remove-watch suodattimet ::alueen-seuraus))
