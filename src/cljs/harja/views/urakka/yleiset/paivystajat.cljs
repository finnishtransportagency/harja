(ns harja.views.urakka.yleiset.paivystajat
  "Urakan yleiset välilehden päivystäjälista ja aikajana."
  (:require [reagent.core :as r]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [cljs-time.core :as t]
            [harja.ui.komponentti :as komp]
            [harja.domain.oikeudet :as oikeudet]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.yleiset :as tiedot]
            [harja.fmt :as fmt]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.ui.aikajana :as aikajana]
            [harja.ui.on-off-valinta :as on-off])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn tallenna-paivystajat [ur paivystajat uudet-paivystajat]
  (log "tallenna päivystäjät!" (pr-str uudet-paivystajat))
  (go (let [tallennettavat
            (into []
                  ;; Kaikki tiedon mankelointi ennen lähetystä tähän
                  (comp (filter #(not (:poistettu %)))
                        (map #(if-let [nimi (:nimi %)]
                                (let [[_ etu suku] (re-matches #"^ *([^ ]+)( *.*?) *$" nimi)]
                                  (assoc %
                                    :etunimi (str/trim etu)
                                    :sukunimi (str/trim suku)))
                                %)))
                  uudet-paivystajat)
            poistettavat
            (into []
                  (keep #(when (and (:poistettu %)
                                    (> (:id %) 0))
                           (:id %)))
                  uudet-paivystajat)
            vastaus (<! (tiedot/tallenna-urakan-paivystajat (:id ur) tallennettavat poistettavat))]
        (if (k/virhe? vastaus)
          (viesti/nayta! "Päivystäjien tallennus epäonnistui." :warning viesti/viestin-nayttoaika-keskipitka)
          (do (reset! paivystajat (reverse (sort-by :loppu vastaus)))
              true)))))

(defn paivystajalista
  [ur paivystajat tallenna!]
  [:div
   [grid/grid
    {:otsikko "Päivystystiedot"
     :tyhja "Ei päivystystietoja."
     :tallenna tallenna!
     :rivin-luokka #(when (and (< (:alku %) (pvm/nyt))
                               (< (pvm/nyt) (:loppu %)))
                      " bold")}
    [{:otsikko "Nimi" :hae #(if-let [nimi (:nimi %)]
                              nimi
                              (str (:etunimi %)
                                   (when-let [suku (:sukunimi %)]
                                     (str " " suku))))
      :aseta (fn [yht arvo]
               (assoc yht :nimi arvo))


      :tyyppi :string :leveys 15
      :validoi [[:ei-tyhja "Anna päivystäjän nimi"]]}
     {:otsikko "Organisaatio" :nimi :organisaatio :fmt :nimi :leveys 10
      :tyyppi :valinta
      :valinta-nayta #(if % (:nimi %) "- Valitse organisaatio -")
      :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]}

     {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys 10
      :pituus 16}
     {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys 10
      :pituus 16}
     {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys 20}
     {:otsikko "Alkupvm" :nimi :alku :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10
      :validoi [[:ei-tyhja "Aseta alkupvm"]
                (fn [alku rivi]
                  (let [loppu (:loppu rivi)
                        id (:id rivi)]
                    (when (and alku loppu
                               (t/before? loppu alku))
                      "Alkupvm ei voi olla lopun jälkeen.")
                    (when (and (= id -1)
                               (t/before? alku (t/today)))
                      "Alkupvm ei voi menneisyydessä.")))]}
     {:otsikko "Loppupvm" :nimi :loppu :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10
      :validoi [[:ei-tyhja "Aseta loppupvm"]
                (fn [loppu rivi]
                  (let [alku (:alku rivi)]
                    (when (and alku loppu
                               (t/before? loppu alku))
                      "Loppupvm ei voi olla alkua ennen.")))]}
     {:otsikko "Vastuuhenkilö" :nimi :vastuuhenkilo :tyyppi :checkbox
      :leveys 10
      :fmt fmt/totuus :tasaa :keskita}]
    paivystajat]
   [yleiset/vihje "Kaikista ilmoituksista lähetetään aina kaikille vuorossaoleville päivystäjille tieto sähköpostilla.
   Uusista toimenpidepyynnöistä lähetetään tekstiviesti vain vuorossaoleville vastuuhenkilöille"]])

(defn- aikajanariveiksi
  "Muunna päivystäjälista aikajanriveiksi. Ryhmitellään tiedot nimen ja organisaation mukaan,
  joten yhdelle päivystäjälle tulee vain yksi uimarata, vaikka hänellä olisi useita
  eri päivystysvuoroja."
  [paivystajat vain-urakoitsijat?]
  (let [ryhmitellyt-paivystykset (->> paivystajat
                                      (group-by (juxt :etunimi :sukunimi :organisaatio))
                                      (sort-by (fn [[[etunimi sukunimi {organisaation-nimi :nimi}] _]]
                                                 [etunimi sukunimi organisaation-nimi])))]
    (for [[[etunimi sukunimi org] paivystykset] ryhmitellyt-paivystykset
          :when (or (not vain-urakoitsijat?)
                    (= :urakoitsija (:tyyppi org)))]
      {::aikajana/otsikko (str etunimi " " sukunimi)
       ::aikajana/ajat (for [{:keys [alku loppu varahenkilo vastuuhenkilo]} paivystykset]
                         {::aikajana/alku alku
                          ::aikajana/loppu loppu
                          ::aikajana/teksti (str (pvm/pvm-aika alku) " \u2013 "
                                                 (pvm/pvm-aika loppu))
                          ::aikajana/vari (cond varahenkilo "yellow"
                                                vastuuhenkilo "green"
                                                :default "gray")})})))

(defn- aikajana-valinnat []
  [:div
   [kentat/tee-kentta {:tyyppi :toggle
                       :paalle-teksti "Näytä aikajana"
                       :pois-teksti "Piilota aikajana"
                       :toggle! tiedot/toggle-nayta-aikajana!} tiedot/nayta-aikajana?]

   (when @tiedot/nayta-aikajana?
     [on-off/on-off "Kaikki" "Vain urakoitsijan henkilöt"
      @tiedot/aikajana-vain-urakoitsijat? tiedot/toggle-aikajana-vain-urakoitsijat!])])

(defn- aikajana [paivystajat]
  [:div.paivystys-aikajana
   [aikajana-valinnat]

   (when @tiedot/nayta-aikajana?
     [:div.paivystajat-aikajana
      [aikajana/aikajana
       {::aikajana/alku (pvm/paivaa-sitten 14)
        ::aikajana/loppu (pvm/paivaa-sitten -60)}
       (aikajanariveiksi paivystajat @tiedot/aikajana-vain-urakoitsijat?)]])])

(defn paivystajat [ur]
  (let [paivystajat (r/atom nil)
        hae! (fn [urakka-id]
               (log "HAETAAN PÄIVYSTÄJÄT: " urakka-id)
               (reset! paivystajat nil)
               (go (reset! paivystajat
                           (reverse (sort-by :loppu
                                             (<! (tiedot/hae-urakan-paivystajat urakka-id)))))))]
    (hae! (:id ur))
    (komp/luo
      (komp/kun-muuttuu (comp hae! :id))
      (fn [ur]
        [:div.paivystajat
         [paivystajalista ur @paivystajat
          (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
            #(tallenna-paivystajat ur paivystajat %))]
         [aikajana @paivystajat]]))))
