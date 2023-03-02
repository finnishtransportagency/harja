(ns harja.tiedot.urakka.aikataulu
  "Ylläpidon urakoiden aikataulu"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<! timeout]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as urakka]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.tyokalut.local-storage :as local-storage]
            [harja.ui.viesti :as viesti]
            [harja.domain.roolit :as roolit]
            [clojure.set :as clj-set]
            [clojure.string :as str])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))


(defonce valmis-tiemerkintaan-modal-data (atom {}))
(defonce tiemerkinta-valmis-modal-data (atom {}))

(defonce aikataulu-nakymassa? (atom false))

(defonce valinnat
  (local-storage/local-storage-atom
    :aikataulu-valinnat
    {:nayta-aikajana? true
     :nayta-tarkka-aikana? false
     :nayta-valitavoitteet? false
     :jarjestys :aika}
    nil))

(defonce nayta-aikajana? (r/cursor valinnat [:nayta-aikajana?]))
(defonce nayta-tarkka-aikajana? (r/cursor valinnat [:nayta-tarkka-aikana?]))
(defonce nayta-valitavoitteet? (r/cursor valinnat [:nayta-valitavoitteet?]))

(defn toggle-nayta-aikajana! []
  (swap! valinnat update :nayta-aikajana? not))

(defn jarjesta-kohteet! [kentta]
  (swap! valinnat assoc :jarjestys kentta))

(defn hae-aikataulu [urakka-id sopimus-id vuosi]
  (k/post! :hae-yllapitourakan-aikataulu {:urakka-id urakka-id
                                          :sopimus-id sopimus-id
                                          :vuosi vuosi}))

(defn hae-tiemerkinnan-suorittavat-urakat [urakka-id]
  (k/post! :hae-tiemerkinnan-suorittavat-urakat {:urakka-id urakka-id}))

(def fimista-haetut-vastaanottajatiedot (atom nil))

(defn merkitse-kohde-valmiiksi-tiemerkintaan [{:keys [kohde-id tiemerkintapvm kopio-itselle? saate
                                                      urakka-id sopimus-id vuosi muut-vastaanottajat]}]
  (let [vastaanottajat (keep #(when (:valittu? %) (:sahkoposti %))
                             (vals @fimista-haetut-vastaanottajatiedot))
        kaikki-vastaanottajat (clj-set/union
                                (into #{} vastaanottajat)
                                (into #{} muut-vastaanottajat))]
    (k/post! :merkitse-kohde-valmiiksi-tiemerkintaan {:kohde-id kohde-id
                                                      :tiemerkintapvm tiemerkintapvm
                                                      :kopio-itselle? kopio-itselle?
                                                      :saate saate
                                                      :vastaanottajat kaikki-vastaanottajat
                                                      :urakka-id urakka-id
                                                      :sopimus-id sopimus-id
                                                      :vuosi vuosi})))

(def ^{:doc "Tähän säilötään modal-dialogista kohteelle asetettavat sähköpostitiedot palvelimelle tallennusta varten."}
kohteiden-sahkopostitiedot (atom nil))

(defn hae-urakan-kayttajat-rooleissa [urakka-id]
  (go
    (let [tiedot (<! (k/post! :hae-urakan-kayttajat-rooleissa
                              {:urakka-id urakka-id
                               :fim-kayttajaroolit roolit/aikataulunakyman-sahkopostiviestinnan-roolit}))
          tiedot (map #(assoc % :roolit (when (:roolit %)
                                          (str/join ", " (:roolit %)))
                                :valittu? true)
                      tiedot)
          muokkausgridin-muodossa (into (sorted-map)
                                       (zipmap (iterate inc 1) tiedot))]
      (reset! fimista-haetut-vastaanottajatiedot muokkausgridin-muodossa))))

(defn tyhjenna-kayttajatiedot []
  (reset! fimista-haetut-vastaanottajatiedot nil))

(def aikataulurivit
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @u/valittu-sopimusnumero
               nakymassa? @aikataulu-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (hae-aikataulu valittu-urakka-id valittu-sopimus-id vuosi))))

(defn- aikataulun-sort-fn-kentan-mukaan
  [kentta-kw]
  (assert (keyword? kentta-kw) "Kentän oltava keyword")
  (juxt #(nil? (kentta-kw %)) kentta-kw))

(def aikataulurivit-suodatettu-jarjestetty
  (reaction (let [tienumero @yllapito-tiedot/tienumero
                  kohdenumero @yllapito-tiedot/kohdenumero
                  aikataulurivit @aikataulurivit
                  jarjestys (:jarjestys @valinnat)]
              (when aikataulurivit
                (let [kohteet (yllapitokohteet/suodata-yllapitokohteet aikataulurivit
                                                                       {:tienumero tienumero
                                                                        :kohdenumero kohdenumero})]
                    (sort-by (case jarjestys
                               :tr tr-domain/tieosoitteen-jarjestys
                               :kohdenumero #(yllapitokohde-domain/kohdenumero-str->kohdenumero-vec (:kohdenumero %))
                               :tiemerkinnan-alku (aikataulun-sort-fn-kentan-mukaan :aikataulu-tiemerkinta-alku)
                               :tiemerkinnan-loppu (aikataulun-sort-fn-kentan-mukaan :aikataulu-tiemerkinta-loppu)
                               :tiemerkinnan-voidaan-aloittaa (aikataulun-sort-fn-kentan-mukaan :valmis-tiemerkintaan)
                               :tiemerkinnan-valmis-viimeistaan (aikataulun-sort-fn-kentan-mukaan :aikataulu-tiemerkinta-takaraja)
                               :aika (aikataulun-sort-fn-kentan-mukaan :aikataulu-paallystys-alku)
                               :paallystyskohde-valmis (aikataulun-sort-fn-kentan-mukaan :aikataulu-kohde-valmis)
                               :paallystyksen-loppu (aikataulun-sort-fn-kentan-mukaan :aikataulu-paallystys-loppu)

                               ;; defaultiksi päällystyksen alku. Älä poista default-arvoa, koska valinnat ovat käyttäjien selaimen local storagessa, ja siellä voi olla vanhoja arvoja, joista voi aiheutua no matching clause.
                               (aikataulun-sort-fn-kentan-mukaan :aikataulu-paallystys-alku))
                             kohteet))))))
(defonce tiemerkinnan-suorittavat-urakat
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               nakymassa? @aikataulu-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id nakymassa?)
                (hae-tiemerkinnan-suorittavat-urakat valittu-urakka-id))))

(defn- kohteen-aikataulun-tila [kentta-aloitettu kentta-valmis pvm-nyt]
  (cond
    (and kentta-aloitettu
         (pvm/jalkeen? kentta-aloitettu pvm-nyt)) :aloittamatta
    (and kentta-valmis
         (pvm/sama-tai-ennen? kentta-valmis pvm-nyt)) :valmis
    (and kentta-valmis
         (pvm/jalkeen? kentta-valmis pvm-nyt)) :kesken
    (and kentta-aloitettu
         (pvm/ennen? kentta-aloitettu pvm-nyt)) :kesken

    :default :aloittamatta))

(defn luokittele-valmiuden-mukaan
  "Luokittelee annetun aikataulurivin valmiuden ja urakkatyypin mukaan kolmeen kategoriaan:
  :kohde-valmis, :kesken, :aloittamatta.

  Jos kohteen aloitus on tulevaisuudessa, palautetaan :aloittamatta
  Jos kohde on merkitty valmiiksi nyt tai menneisyydessä, palautetaan :valmis
  Jos kohde on merkitty valmiiksi tulevaisuudessa, palautetaan :kesken
  Jos kohde on aloitettu menneisyydessä, palautetaan :kesken
  Muuten palautetaan :aloittamatta"
  [{kohde-aloitettu :aikataulu-kohde-alku
    kohde-valmis :aikataulu-kohde-valmis
    tiemerkinta-aloitettu :aikataulu-tiemerkinta-alku
    tiemerkinta-lopetettu :aikataulu-tiemerkinta-loppu
    :as aikataulurivi} urakkatyyppi pvm-nyt]
  (case urakkatyyppi
    (:paallystys :paikkaus)
    (kohteen-aikataulun-tila kohde-aloitettu kohde-valmis pvm-nyt)

    :tiemerkinta
    (kohteen-aikataulun-tila tiemerkinta-aloitettu tiemerkinta-lopetettu pvm-nyt)))

(defn aikataulurivit-valmiuden-mukaan [aikataulurivit urakkatyyppi]
  (group-by #(luokittele-valmiuden-mukaan %
                                          urakkatyyppi
                                          (pvm/nyt))
            aikataulurivit))

(defn aikapohjainen-jarjestys-valittu?
  "Palauttaa totuudellisen arvon, jos valinta on jokin aikaan perustuva järjestyssuodatin."
  [valinta]
  (#{:aika
     :tiemerkinnan-alku
     :tiemerkinnan-loppu
     :tiemerkinnan-valmius
     :tiemerkinnan-takaraja
     :paallystyksen-loppu} valinta))

(defn muunna-aikataulurivit-tallennukseen
  "Muuntaa aikataulurivit tallennusta varten"
  [kohteet]
  (mapv #(assoc % :aikataulu-tiemerkinta-merkinta
                  (when-not (= "ei valittu" (:aikataulu-tiemerkinta-merkinta %))
                    (:aikataulu-tiemerkinta-merkinta %))
                  :aikataulu-tiemerkinta-jyrsinta
                  (when-not (= "ei valittu" (:aikataulu-tiemerkinta-jyrsinta %))
                    (:aikataulu-tiemerkinta-jyrsinta %)))
        kohteet))

(defn- tallenna-yllapitokohteiden-aikataulu
  [{:keys [urakka-id sopimus-id vuosi kohteet onnistui-fn epaonnistui-fn]}]
  (go
    (let [vastaus (<! (k/post! :tallenna-yllapitokohteiden-aikataulu
                               {:urakka-id urakka-id
                                :sopimus-id sopimus-id
                                :vuosi vuosi
                                :kohteet (muunna-aikataulurivit-tallennukseen kohteet)}))]
      (if (k/virhe? vastaus)
        (epaonnistui-fn)
        (onnistui-fn vastaus)))))

(defn- lisaa-kohteille-sahkopostilahetystiedot [kohteet]
  (mapv (fn [kohde]
         (assoc kohde :sahkopostitiedot (get @kohteiden-sahkopostitiedot (:id kohde))))
       kohteet))

(defn tallenna-aikataulu [urakka-id sopimus-id vuosi kohteet onnistui-fn]
  (tallenna-yllapitokohteiden-aikataulu
    {:urakka-id urakka-id
     :sopimus-id sopimus-id
     :vuosi vuosi
     :kohteet (lisaa-kohteille-sahkopostilahetystiedot kohteet)
     :onnistui-fn onnistui-fn
     :epaonnistui-fn #(viesti/nayta! "Tallennus epäonnistui!"
                                     :warning
                                     viesti/viestin-nayttoaika-lyhyt)}))
