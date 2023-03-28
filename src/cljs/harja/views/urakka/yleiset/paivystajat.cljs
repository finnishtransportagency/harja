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
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.yleiset :as tiedot]
            [harja.fmt :as fmt]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as tapahtumat]
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
        (if (or (k/virhe? vastaus) (get-in vastaus [:vastaus :virhe]))
          (viesti/nayta! (str "Päivystäjien tallennus epäonnistui.\n\n"
                              (get-in vastaus [:vastaus :virhe]))
                         :warning viesti/viestin-nayttoaika-pitka)
          (do (reset! paivystajat (reverse (sort-by :loppu vastaus)))
              true)))))

(defn- paivystys-voimassa?
  [paivystys]
  (and (< (:alku paivystys) (pvm/nyt))
       (< (pvm/nyt) (:loppu paivystys))))

(defn paivystajalista
  [ur paivystajat tallenna!]
  (let [varoita-paivystyksen-puuttumisesta? (and
                                              ;; Ennen kuin päivystäjät on haettu, tämä on nil. Älä näytä silloin varoitusta
                                              (not (nil? paivystajat))
                                              (urakka-tiedot/urakka-kaynnissa? ur)
                                              (not-any? #(paivystys-voimassa? %) paivystajat))]
    [:div
     [grid/grid
      {:otsikko "Päivystystiedot"
       :tyhja "Ei päivystystietoja."
       :tallenna tallenna!
       :rivin-luokka #(when (paivystys-voimassa? %)
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
       {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys 20
        :validoi [[:email "Kirjoita sähköpostiosoite loppuun ilman ääkkösiä."]]}
       {:otsikko "Alkupvm" :nimi :alku :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10
        :validoi [[:ei-tyhja "Aseta alkupvm"]
                  (fn [alku rivi]
                    (let [id (:id rivi)
                          loppu (:loppu rivi)]
                      (when (and alku loppu
                                 (t/before? loppu alku))
                        "Loppupvm ei voi olla alkua ennen.")
                      (when (and (neg? id)
                                 alku
                                 (t/before? alku (pvm/paivan-alussa-opt (pvm/nyt))))
                        "Et saa asettaa uuden päivystyksen alkamishetkeä menneisyyteen.")))]}
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
     (if varoita-paivystyksen-puuttumisesta?
       [yleiset/info-laatikko :varoitus
        "Urakka on käynnissä mutta vuorossaoleva päivystäjä puuttuu!"
        "Tieliikenneilmoituksia ei voi toimittaa urakkaan. Ole hyvä ja lisää vuorossaoleva päivystäjä." nil]
       [yleiset/vihje "Kaikista ilmoituksista lähetetään aina kaikille vuorossaoleville päivystäjille tieto sähköpostilla.
   Uusista toimenpidepyynnöistä lähetetään tekstiviesti vain vuorossaoleville vastuuhenkilöille"])]))

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
                       :paalle-teksti "Näytä päivystäjien aikajana"
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
