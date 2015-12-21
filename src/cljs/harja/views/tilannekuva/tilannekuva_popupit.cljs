(ns harja.views.tilannekuva.tilannekuva-popupit
  (:require [harja.tiedot.navigaatio :as nav]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.tiedot.ilmoitukset :as ilmoitukset]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.tilannekuva.historiakuva :as tiedot]
            [clojure.string :as str]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.paikkauksen-kohdeluettelo :as paikkaus]
            [harja.views.urakka.paallystyksen-kohdeluettelo :as paallystys]
            [harja.domain.paallystys.pot :as paallystys-pot]))

(def klikattu-tyokone (atom nil))

(defn- rakenna [kartta]
  "Funktio, joka rakentaa mapistä hiccup-rakenteen. Ei ole tarkoituskaan olla kovin älykäs, vaan helpottaa lähinnä
  kehitystyötä."
  (log (pr-str kartta))
  (for [avain (keys kartta)]
    (cond
      (map? (get kartta avain)) (into [:div [:b (pr-str avain)]] (rakenna (get kartta avain)))

      (or (set? (get kartta avain)) (vector? (get kartta avain)))
      [:div [:b (pr-str avain)] (clojure.string/join ", " (get kartta avain))]

      :else [:div [:b (pr-str avain)] (pr-str (get kartta avain))])))

(defn tee-arvolistaus-popup
  ([otsikko nimi-arvo-parit] (tee-arvolistaus-popup otsikko nimi-arvo-parit nil))
  ([otsikko nimi-arvo-parit {:keys [paaluokka linkki]}]
   [:div {:class (str "kartta-popup " (when paaluokka
                                        paaluokka))}
    [:p [:b otsikko]]

    [:table.otsikot-ja-arvot
     (for [[nimi arvo] nimi-arvo-parit]
       (when-not (or (nil? arvo)
                     (empty? arvo))
         ^{:key (str nimi arvo)}
         [:tr
          [:td.otsikko [:b nimi]]
          [:td.arvo arvo]]))]

    (when linkki
      (let [nimi (:nimi linkki)
            on-click (:on-click (:nimi linkki))]
        [:a.arvolistaus-linkki {:href     "#"
                                :on-click on-click}
         nimi]))]))

(defmulti nayta-popup :aihe)

(defn- viivan-keskella [tapahtuma]
  (if-let [reittipisteet (or (get-in tapahtuma [:alue :points])
                             (mapcat :points (get-in tapahtuma [:sijainti :lines])))]
    (nth reittipisteet (int (/ (count reittipisteet) 2)))))

(defn geometrian-koordinaatti [tapahtuma]
  (if-let [piste (get-in tapahtuma [:sijainti :coordinates])]
    piste
    (viivan-keskella tapahtuma)))

(defmethod nayta-popup :toteuma-klikattu [tapahtuma]
  (kartta/nayta-popup! (viivan-keskella tapahtuma)
                       (tee-arvolistaus-popup "Toteuma"
                                              [["Aika" (pvm/pvm (:alkanut tapahtuma)) "-" (pvm/pvm (:paattynyt tapahtuma))]
                                               ["Suorittaja" (get-in tapahtuma [:suorittaja :nimi])]
                                               ["Tehtävät" (doall
                                                             (for [tehtava (:tehtavat tapahtuma)]
                                                               [:div.toteuma-tehtavat
                                                                [:div "Toimenpide: " (:toimenpide tehtava)]
                                                                [:div "Määrä: " (:maara tehtava)]
                                                                (when (:paivanhinta tehtava)
                                                                  [:div "Päivän hinta: " (:paivanhinta tehtava)])
                                                                (when (:lisatieto tehtava)
                                                                  [:div "Lisätieto: " (:lisatieto tehtava)])]))]
                                               ["Materiaalit" (for [toteuma (:materiaalit tapahtuma)]
                                                                [:div.toteuma-materiaalit
                                                                 [:div "Materiaali: " (get-in toteuma [:materiaali :nimi])]
                                                                 [:div "Määrä: " (:maara toteuma)]])]
                                               ["Lisätieto" (:lisatieto tapahtuma)]])))

(defmethod nayta-popup :ilmoitus-klikattu [tapahtuma]
  (kartta/nayta-popup! (geometrian-koordinaatti tapahtuma)
                       (tee-arvolistaus-popup (if (= :toimenpidepyynto (:ilmoitustyyppi tapahtuma))
                                                "Toimenpidepyyntö"
                                                (str/capitalize (name (:ilmoitustyyppi tapahtuma))))
                                              [["Ilmoitettu" (pvm/pvm-aika-sek (:ilmoitettu tapahtuma))]
                                               ["Selite" (:lyhytselite tapahtuma)]
                                               ["Kuittaukset" (count (:kuittaukset tapahtuma))]]
                                              {:linkki {:nimi     "Siirry ilmoitusnäkymään" ; FIXME Ei vaikuta toimivan?
                                                        :on-click #(do (.preventDefault %)
                                                                       (let [putsaa (fn [asia]
                                                                                      (dissoc asia :type :alue))]
                                                                         (reset! nav/sivu :ilmoitukset)
                                                                         (reset! ilmoitukset/haetut-ilmoitukset
                                                                                 (map putsaa (filter
                                                                                               (fn [asia] (= (:type asia) :ilmoitus))
                                                                                               @tiedot/historiakuvan-asiat-kartalla)))
                                                                         (reset! ilmoitukset/valittu-ilmoitus (putsaa tapahtuma))))}})))

(defmethod nayta-popup :tyokone-klikattu [tapahtuma]
  (reset! klikattu-tyokone (:tyokoneid tapahtuma))
  (kartta/keskita-kartta-pisteeseen (:sijainti tapahtuma))

  (kartta/nayta-popup! (:sijainti tapahtuma)
                       (tee-arvolistaus-popup "Työkone"
                                              [["Tyyppi" (:tyokonetyyppi tapahtuma)]
                                               ["Viimeisin paikka\u00ADtieto" (pvm/pvm-aika-sek (:lahetysaika tapahtuma))]
                                               ["Organisaatio" (:organisaationimi tapahtuma)]
                                               ["Urakka" (:urakkanimi tapahtuma)]
                                               ["Tehtävät" (let [tehtavat (str/join ", " (:tehtavat tapahtuma))]
                                                             [:span tehtavat])]])))

(defmethod nayta-popup :uusi-tyokonedata [data]
  (when-let [tk @klikattu-tyokone]
    (when-let [haettu (first (filter #(= tk (:tyokoneid %))
                                     (:tyokoneet data)))]
      (kartta/keskita-kartta-pisteeseen (:sijainti haettu))
      (kartta/poista-popup!)
      (nayta-popup (assoc haettu :aihe :tyokone-klikattu)))))


(defmethod nayta-popup :laatupoikkeama-klikattu [tapahtuma]
  (kartta/nayta-popup! (geometrian-koordinaatti tapahtuma)
                       (tee-arvolistaus-popup "Laatupoikkeama"
                                              [["Aika" (pvm/pvm-aika-sek (:aika tapahtuma))]
                                               ["Tekijä" (:tekijanimi tapahtuma) ", " (name (:tekija tapahtuma))]
                                               ["Päätös" (str (laatupoikkeamat/kuvaile-paatostyyppi (get-in tapahtuma [:paatos :paatos]))
                                                              " (" (pvm/pvm-aika (get-in tapahtuma [:paatos :kasittelyaika])) ")")]])))

(defmethod nayta-popup :tarkastus-klikattu [tapahtuma]
  (kartta/nayta-popup! (geometrian-koordinaatti tapahtuma)
                       (tee-arvolistaus-popup (str/capitalize (name (:tyyppi tapahtuma)))
                                              [["Aika" (pvm/pvm-aika-sek (:aika tapahtuma))]
                                               ["Mittaaja" (:mittaaja tapahtuma)]])))

(defmethod nayta-popup :turvallisuuspoikkeama-klikattu [tapahtuma]
  (kartta/nayta-popup! (geometrian-koordinaatti tapahtuma)
                       (tee-arvolistaus-popup "Turvallisuuspoikkeama"
                                              [["Tapahtunut" (pvm/pvm-aika (:tapahtunut tapahtuma)) " - " (pvm/pvm-aika (:paattynyt tapahtuma))]
                                               ["Käsitelty" (pvm/pvm-aika (:kasitelty tapahtuma))]
                                               ["Työ\u00ADtehtävä" (:tyontekijanammatti tapahtuma) ", " (:tyotehtava tapahtuma)]
                                               ["Vammat" (:vammat tapahtuma)]
                                               ["Sairaala\u00ADvuorokaudet" (:sairaalavuorokaudet tapahtuma)]
                                               ["Sairaus\u00ADpoissaolo\u00ADpäivät" (:sairauspoissaolopaivat tapahtuma)]
                                               ["Kuvaus" (:kuvaus tapahtuma)]
                                               ["Korjaavat toimen\u00ADpiteet" (count (filter :suoritettu (:korjaavattoimenpiteet tapahtuma)))
                                                "/" (count (:korjaavattoimenpiteet tapahtuma))]])))

(defmethod nayta-popup :paallystys-klikattu [tapahtuma]
  (log "Tapahtuma:" (pr-str tapahtuma))
  (kartta/nayta-popup! (geometrian-koordinaatti tapahtuma)
                       (tee-arvolistaus-popup "Päällystyskohde"
                                              [["Nimi" (get-in tapahtuma [:kohde :nimi])]
                                               ["Tie\u00ADrekisteri\u00ADkohde" (get-in tapahtuma [:kohdeosa :nimi])]
                                               ["Osoite" (yleiset/tierekisteriosoite
                                                           (get-in tapahtuma [:tr :numero])
                                                           (get-in tapahtuma [:tr :alkuosa])
                                                           (get-in tapahtuma [:tr :alkuetaisyys])
                                                           (get-in tapahtuma [:tr :loppuosa])
                                                           (get-in tapahtuma [:tr :loppuetaisyys]))]
                                               ["Nykyinen päällyste: " (paallystys-pot/hae-paallyste-koodilla (:nykyinen_paallyste tapahtuma))]
                                               ["Toimenpide" (:toimenpide tapahtuma)]
                                               ["Tila" (paallystys/kuvaile-kohteen-tila (:toimenpide tapahtuma))]])))

(defmethod nayta-popup :paikkaus-klikattu [tapahtuma]
  (kartta/nayta-popup! (geometrian-koordinaatti tapahtuma)
                       (tee-arvolistaus-popup "Paikkauskohde"
                                              [["Nimi" (get-in tapahtuma [:kohde :nimi])]
                                               ["Tie\u00ADrekisteri\u00ADkohde" (get-in tapahtuma [:kohdeosa :nimi])]
                                               ["Osoite" (yleiset/tierekisteriosoite
                                                           (get-in tapahtuma [:tr :numero])
                                                           (get-in tapahtuma [:tr :alkuosa])
                                                           (get-in tapahtuma [:tr :alkuetaisyys])
                                                           (get-in tapahtuma [:tr :loppuosa])
                                                           (get-in tapahtuma [:tr :loppuetaisyys]))]
                                               ["Nykyinen päällyste: " (paallystys-pot/hae-paallyste-koodilla (:nykyinen_paallyste tapahtuma))]
                                               ["Toimenpide" (:toimenpide tapahtuma)]
                                               ["Tila" (paikkaus/kuvaile-kohteen-tila (:toimenpide tapahtuma))]])))
