(ns harja.tiedot.urakka.turvallisuuspoikkeamat
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.pvm :as pvm]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.urakka :as urakka-d])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))

(def kayttajahakulomake-data (atom nil))
(def kayttajahakutulokset-data (atom []))

(defonce valittu-turvallisuuspoikkeama (atom nil))

(def turvallisuuspoikkeaman-luonti-kesken? (atom false))

(defn hae-urakan-turvallisuuspoikkeamat
  [urakka-id [alku loppu]]
  (k/post! :hae-turvallisuuspoikkeamat {:urakka-id urakka-id
                                        :alku alku
                                        :loppu loppu}))

(defn hae-turvallisuuspoikkeama [urakka-id turvallisuuspoikkeama-id]
  (k/post! :hae-turvallisuuspoikkeama {:urakka-id urakka-id
                                       :turvallisuuspoikkeama-id turvallisuuspoikkeama-id}))

(defn hae-kayttajat [hakuparametrit]
  (k/post! :hae-turvallisuuspoikkeaman-hakulomakkeen-kayttajat
           {:urakka-id (:urakka-id hakuparametrit)
            :etunimi (:etunimi hakuparametrit)
            :sukunimi (:sukunimi hakuparametrit)
            :kayttajanimi (:kayttajanimi hakuparametrit)}))

(defonce haetut-turvallisuuspoikkeamat
         (reaction<! [urakka-id (:id @nav/valittu-urakka)
                      hoitokausi @urakka/valittu-hoitokausi
                      nakymassa? @nakymassa?]
                     {:nil-kun-haku-kaynnissa? true}
                     (when nakymassa?
                       (hae-urakan-turvallisuuspoikkeamat urakka-id hoitokausi))))

(def karttataso-turvallisuuspoikkeamat (atom false))

(defonce turvallisuuspoikkeamat-kartalla
         (reaction
           (let [valittu-turvallisuuspoikkeama-id (:id @valittu-turvallisuuspoikkeama)]
             (when @karttataso-turvallisuuspoikkeamat
               (kartalla-esitettavaan-muotoon
                 @haetut-turvallisuuspoikkeamat
                 #(= valittu-turvallisuuspoikkeama-id (:id %))
                 (comp (keep #(and (:sijainti %) %)) ;; vain ne, joissa on sijainti
                       (map #(assoc % :tyyppi-kartalla :turvallisuuspoikkeama))))))))

(defn kasaa-tallennettava-turpo
  [tp]
  {:tp (-> tp
           (dissoc :liitteet :kommentit :korjaavattoimenpiteet :uusi-kommentti)
           (assoc :urakka (:id @nav/valittu-urakka))
           (cond-> (not= (:tyontekijanammatti tp) :muu_tyontekija)
                   (dissoc :tyontekijanammattimuu)
                   (not (some #{:henkilovahinko} (:vahinkoluokittelu tp)))
                   (dissoc :tyontekijanammatti)))
   :korjaavattoimenpiteet (remove #(empty? (dissoc % :id :koskematon)) (:korjaavattoimenpiteet tp))
   ;; Lomakkeessa voidaan lisätä vain yksi kommentti kerrallaan, joka menee uusi-kommentti avaimeen
   ;; Täten tallennukseen ei tarvita :liitteitä eikä :kommentteja
   ;:liitteet           (:liitteet tp)
   ;:kommentit          (:kommentit tp)
   :uusi-kommentti (:uusi-kommentti tp)
   :hoitokausi @urakka/valittu-hoitokausi})

(defn tallenna-turvallisuuspoikkeama
  [tp]
  (k/post! :tallenna-turvallisuuspoikkeama (kasaa-tallennettava-turpo tp)))

(defn turvallisuuspoikkeaman-tallennus-onnistui
  [turvallisuuspoikkeamat]
  (reset! haetut-turvallisuuspoikkeamat turvallisuuspoikkeamat))

(def +uusi-turvallisuuspoikkeama+
  ^{:uusi? true}
  {:tila :avoin
   :vakavuusaste :lieva
   :tyontekijanammatti :muu_tyontekija})

(defn uusi-turvallisuuspoikkeama [urakka-id]
  (when (not @turvallisuuspoikkeaman-luonti-kesken?)
    (reset! turvallisuuspoikkeaman-luonti-kesken? true)
    (go
      (let [vastaus (<! (k/post! :hae-urakan-kuluvan-vuosikolmanneksen-tyotunnit
                                 {::urakan-tyotunnit/urakka-id urakka-id}))]
        (if (k/virhe? vastaus)
          (viesti/nayta! "Urakan työtuntien haku epäonnistui!" :warning viesti/viestin-nayttoaika-lyhyt)
          (let [tyotunnit (get-in vastaus [::urakan-tyotunnit/urakan-tyotunnit ::urakan-tyotunnit/tyotunnit])]
            (reset! valittu-turvallisuuspoikkeama (assoc +uusi-turvallisuuspoikkeama+
                                                    :urakan-tyotunnit tyotunnit
                                                    :vaylamuoto (if (urakka-d/vesivaylaurakka? @nav/valittu-urakka)
                                                                  :vesi
                                                                  :tie)))))

        (reset! turvallisuuspoikkeaman-luonti-kesken? false)))))
