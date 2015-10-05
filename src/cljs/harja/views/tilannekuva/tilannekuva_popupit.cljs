(ns harja.views.tilannekuva.tilannekuva-popupit
  (:require [harja.tiedot.navigaatio :as nav]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.tiedot.ilmoitukset :as ilmoitukset]
            [harja.tiedot.tilannekuva.historiakuva :as tiedot]
            [clojure.string :as str]))

(def klikattu-tyokone (atom nil))

(defmulti nayta-popup :aihe)

(defmethod nayta-popup :toteuma-klikattu [tapahtuma]
  (log "toteuma-klikattu")
  (log (pr-str (dissoc tapahtuma :reittipisteet)))
  (log (pr-str (:tehtava tapahtuma)))
  (log (pr-str (get-in tapahtuma [:tehtava :id])))
  (kartta/nayta-popup! (:klikkaus-koordinaatit tapahtuma)
                       [:div.kartta-toteuma-popup
                        [:p [:b "Toteuma"]]
                        [:p "Aika: " (pvm/pvm (:alkanut tapahtuma)) "-" (pvm/pvm (:paattynyt tapahtuma))]
                        (when (:suorittaja tapahtuma)
                          [:span
                           [:p "Suorittaja: " (get-in tapahtuma [:suorittaja :nimi])]])
                        (when-not (empty? (:tehtavat tapahtuma))
                          (doall
                            (for [tehtava (:tehtavat tapahtuma)]
                              [:span
                               [:p "Toimenpide: " (:toimenpide tehtava)]
                               [:p "Määrä: " (:maara tehtava)]
                               [:p "Päivän hinta: " (:paivanhinta tehtava)]
                               [:p "Lisätieto: " (:lisatieto tehtava)]])))
                        (when-not (empty? (:materiaalit tapahtuma))
                          (doall
                            (for [toteuma (:materiaalit tapahtuma)]
                              [:span
                               [:p "Materiaali: " (get-in toteuma [:materiaali :nimi])]
                               [:p "Määrä: " (:maara toteuma)]])))
                        (when (:lisatieto tapahtuma)
                          [:p "Lisätieto: " (:lisatieto tapahtuma)])]))


(defmethod nayta-popup :reittipiste-klikattu [tapahtuma]
  (log "reittipiste-klikattu")
  (kartta/nayta-popup! (get-in tapahtuma [:sijainti :coordinates])
                       [:div.kartta-reittipiste-popup
                        [:p [:b "Reittipiste"]]
                        [:p "Aika: " (pvm/pvm (:aika tapahtuma))]
                        (when (get-in tapahtuma [:tehtava :id])
                          [:span
                           [:p "Toimenpide: " (get-in tapahtuma [:tehtava :toimenpide])]
                           [:p "Määrä: " (get-in tapahtuma [:tehtava :maara])]])
                        (when (get-in tapahtuma [:materiaali :id])
                          [:span
                           [:p "Materiaali: " (get-in tapahtuma [:materiaali :nimi])]
                           [:p "Määrä: " (get-in tapahtuma [:materiaali :maara])]])]))

(defmethod nayta-popup :ilmoitus-klikattu [tapahtuma]
  (log "ilmoitus klikattu!")
  (kartta/nayta-popup! (get-in tapahtuma [:sijainti :coordinates])
                       [:div.kartta-ilmoitus-popup
                        (log (pr-str tapahtuma))
                        [:p [:b (name (:ilmoitustyyppi tapahtuma))]]
                        [:p "Ilmoitettu: " (pvm/pvm-aika-sek (:ilmoitettu tapahtuma))]
                        [:p "Vapaateksti: " (:vapaateksti tapahtuma)]
                        [:p (count (:kuittaukset tapahtuma)) " kuittausta."]
                        [:a {:href     "#"
                             :on-click #(do (.preventDefault %)
                                            (let [putsaa (fn [asia]
                                                           (dissoc asia :type :alue))]
                                              (reset! nav/sivu :ilmoitukset)
                                              (reset! ilmoitukset/haetut-ilmoitukset
                                                      (map putsaa (filter
                                                                    (fn [asia] (= (:type asia) :ilmoitus))
                                                                    @tiedot/historiakuvan-asiat-kartalla)))
                                              (reset! ilmoitukset/valittu-ilmoitus (putsaa tapahtuma))
                                              ))}
                         "Siirry ilmoitusnäkymään"]]))

(defmethod nayta-popup :tyokone-klikattu [tapahtuma]
  (reset! klikattu-tyokone (:tyokoneid tapahtuma))
  (kartta/keskita-kartta-pisteeseen (:sijainti tapahtuma))

  (kartta/nayta-popup! (:sijainti tapahtuma)
                       [:div.kartta-tyokone-popup
                        [:p [:b "Työkone"]]
                        [:div "Tyyppi: " (:tyokonetyyppi tapahtuma)]
                        [:div "Organisaatio: " (:organisaationimi tapahtuma)]
                        [:div "Urakka: " (:urakkanimi tapahtuma)]
                        [:div "Tehtävät: "
                         (let [tehtavat (str/join ", " (:tehtavat tapahtuma))]
                           [:span tehtavat])]]))

(defmethod nayta-popup :uusi-tyokonedata [data]
  (when-let [tk @klikattu-tyokone]
    (when-let [haettu (first (filter #(= tk (:tyokoneid %))
                                     (:tyokoneet data)))]
      (kartta/keskita-kartta-pisteeseen (:sijainti haettu))
      (kartta/poista-popup!)
      (nayta-popup (assoc haettu :aihe :tyokone-klikattu)))))

(defn- rakenna [kartta]
  "Funktio, joka rakentaa mapistä hiccup-rakenteen. Ei ole tarkoituskaan olla kovin älykäs, vaan helpottaa lähinnä
  kehitystyötä."
  (log (pr-str kartta))
  (for [avain (keys kartta)]
    (cond
      (map? (get kartta avain)) (into [:p [:b (pr-str avain)]] (rakenna (get kartta avain)))

      (or (set? (get kartta avain)) (vector? (get kartta avain)))
      [:p [:b (pr-str avain)] (clojure.string/join ", " (get kartta avain))]

      :else [:p [:b (pr-str avain)] (pr-str (get kartta avain))])))

(defmethod nayta-popup :havainto-klikattu [tapahtuma]
  (log "Näytä popup!")
  (kartta/nayta-popup! (:klikkaus-koordinaatit tapahtuma)
                       [:div.kartta-popup
                        [:p [:b "Havainto"]]
                        (into [:div] (rakenna (dissoc tapahtuma :sijainti :aihe :klikkaus-koordinaatit :alue :type :tilannekuvatyyppi)))]))

(defmethod nayta-popup :tarkastus-klikattu [tapahtuma]
  (log "Näytä popup!")
  (kartta/nayta-popup! (:klikkaus-koordinaatit tapahtuma)
                       [:div.kartta-popup
                        [:p [:b (str/capitalize (name (:tyyppi tapahtuma)))]]
                        [:p "Aika: " (pvm/pvm-aika-sek (:aika tapahtuma))]
                        [:p "Mittaaja: " (:mittaaja tapahtuma)]]))

(defmethod nayta-popup :turvallisuuspoikkeama-klikattu [tapahtuma]
  (log "Näytä popup!")
  (kartta/nayta-popup! (:klikkaus-koordinaatit tapahtuma)
                       [:div.kartta-popup
                        [:p [:b (str/join ", " (map (comp str/capitalize name) (:tyyppi tapahtuma)))]]
                        [:p (pvm/pvm-aika (:tapahtunut tapahtuma)) " - " (pvm/pvm-aika (:paattynyt tapahtuma))]
                        [:p "Käsitelty: " (pvm/pvm-aika (:kasitelty tapahtuma))]
                        [:p "Työtehtävä: " (:tyontekijanammatti tapahtuma) ", " (:tyotehtava tapahtuma)]
                        [:p (:vammat tapahtuma) ", " (:sairaalavuorokaudet tapahtuma) "/" (:sairauspoissaolopaivat tapahtuma)]
                        [:p (:kuvaus tapahtuma)]
                        [:p "Korjaavat toimenpiteet: "
                         (count (filter :suoritettu (:korjaavattoimenpiteet tapahtuma)))
                         "/" (count (:korjaavattoimenpiteet tapahtuma))]]))

(defmethod nayta-popup :paallystyskohde-klikattu [tapahtuma]
  (log "Näytä popup!")
  (kartta/nayta-popup! (:klikkaus-koordinaatit tapahtuma)
                       [:div.kartta-popup
                        [:p [:b "Otsikko"]]
                        (into [:div] (rakenna (dissoc tapahtuma :sijainti :aihe :klikkaus-koordinaatit :alue :type :tilannekuvatyyppi)))]))

(defmethod nayta-popup :paikkaustoteuma-klikattu [tapahtuma]
  (log "Näytä popup!")
  (kartta/nayta-popup! (:klikkaus-koordinaatit tapahtuma)
                       [:div.kartta-popup
                        [:p [:b "Otsikko"]]
                        (into [:div] (rakenna (dissoc tapahtuma :sijainti :aihe :klikkaus-koordinaatit :alue :type :tilannekuvatyyppi)))]))