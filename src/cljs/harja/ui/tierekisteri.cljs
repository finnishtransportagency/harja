(ns harja.ui.tierekisteri
  "Tierekisteriosoitteiden näyttämiseen, muokkaamiseen ja karttavalintaan liittyvät komponentit."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.vkm :as vkm]
            [cljs.core.async :refer [>! <! alts! chan] :as async])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn tieosoite
  "Näyttää tieosoitteen muodossa tienumero/tieosa/alkuosa/alkuetäisyys - tienumero//loppuosa/loppuetäisyys.
  Jos ei kaikkia kenttiä ole saatavilla, palauttaa 'ei saatavilla' -viestin"
  [numero aosa aet losa lopet]
  (let [laita (fn [arvo]
                (if (or
                      (and (number? arvo) (not (nil? arvo)))
                      (not (empty? arvo))) arvo "?"))]
    (if (and numero aosa aet losa lopet)
      [:span (str (laita numero) " / " (laita aosa) " / " (laita aet) " - " (laita losa) " / " (laita lopet))]
      ;; mahdollistetaan pistemisen sijainnin näyttäminen
      (if (and numero aosa aet)
        [:span (str (laita numero) " / " (laita aosa) " / " (laita aet))]
        [:span "Tieosoitetta ei saatavilla"]))))

(defn karttavalitsin
  "Komponentti TR-osoitteen (pistemäisen tai välin) valitsemiseen kartalta.
Asettaa kartan näkyviin, jos se ei ole jo näkyvissä, ja keskittää sen
löytyneeseen pisteeseen.

Optiot on mäppi parametreja, jossa seuraavat avaimet:

  :kun-valmis  Funktio, jota kutsutaan viimeisenä kun käyttäjän valinta on valmis.
               Parametrina valittu osoite mäppi, jossa avaimet:
               :numero, :alkuosa, :alkuetaisyys, :loppuosa, :loppuetaisyys
               Jos käyttäjä valitsi pistemäisen osoitteen, loppuosa ja -etäisyys
               avaimia ei ole mäpissä.

  :kun-peruttu Funktio, jota kutsutaan, jos käyttäjä haluaa perua karttavalinnan 
               ilman TR-osoitteen päivittämistä. Ei parametrejä.

  :paivita     Funktio, jota kutsutaan kun valittu osoite muuttuu. Esim. 
               kun käyttäjä valitsee alkupisteen, kutsutaan tätä funktiota
               osoitteella, jossa ei ole vielä loppupistettä.
"
  [optiot]
  (let [tapahtumat (chan)
        tila (atom :ei-valittu)
        alkupiste (atom nil)
        tr-osoite (atom {})
        ;; Pidetään optiot atomissa, jota päivitetään will-receive-props tapahtumassa
        ;; Muuten go lohko sulkee alkuarvojen yli
        optiot (cljs.core/atom optiot)

        virhe (atom nil)]
    
    (go (loop [vkm-haku nil]
          (let [[arvo kanava] (alts! (if vkm-haku
                                       [vkm-haku tapahtumat]
                                       [tapahtumat]))]
            (when arvo
              (if (= kanava vkm-haku)
                ;; Saatiin VKM vastaus paikkahakuun, käsittele se
                (let [{:keys [kun-valmis paivita]} @optiot
                      osoite arvo] 
                  (if (vkm/virhe? osoite)
                    (do (reset! virhe (vkm/virhe osoite))
                        (recur nil))
                    
                    (do
                      (reset! virhe nil) ;; poistetaan mahdollinen aiempi virhe
                      (case @tila
                        :ei-valittu
                        (let [osoite (swap! tr-osoite
                                            (fn [tr]
                                              (dissoc (merge tr
                                                             {:numero (:tie osoite)
                                                              :alkuosa (:aosa osoite)
                                                              :alkuetaisyys (:aet osoite)})
                                                      :loppuosa
                                                      :loppuetaisyys)))]
                          (paivita osoite)
                          (reset! tila :alku-valittu))
                        
                        :alku-valittu
                        (let [osoite (swap! tr-osoite
                                            merge 
                                            {:numero (:tie osoite)
                                             :alkuosa (:aosa osoite)
                                             :alkuetaisyys (:aet osoite)
                                             :loppuosa (:losa osoite)
                                             :loppuetaisyys (:let osoite)
                                             :geometria (:geometria osoite)})]
                          (kun-valmis osoite)))
                      (recur nil))))
                
                ;; Saatiin uusi tapahtuma, jos se on klik, laukaise haku
                (let [{:keys [tyyppi sijainti x y]} arvo]
                  (case tyyppi
                    ;; Hiirtä liikutellaan kartan yllä, aseta tilan mukainen tooltip
                    :hover
                    (kartta/aseta-tooltip! x y (case @tila
                                                 :ei-valittu "Klikkaa alkupiste"
                                                 :alku-valittu "Klikkaa loppupiste tai hyväksy pistemäinen enter näppäimellä"))

                    ;; Enter näppäimellä voi hyväksyä pistemäisen osoitteen
                    :enter (when (= @tila :alku-valittu)
                             ((:kun-valmis @optiot) @tr-osoite))
                    nil)
                  
                  (recur (if (= :click tyyppi)
                           (if (= :alku-valittu @tila)
                             (vkm/koordinaatti->trosoite-kahdella @alkupiste sijainti)
                             (do
                               (reset! alkupiste sijainti) 
                               (vkm/koordinaatti->trosoite sijainti)))
                           vkm-haku))))))))
    
    (komp/luo
     {:component-will-receive-props
      (fn [_ _ uudet-optiot]
        (reset! optiot uudet-optiot))}
     
     (komp/sisaan #(nav/vaihda-kartan-koko! :M))
     (komp/sisaan-ulos #(kartta/aseta-kursori! :crosshair)
                       #(kartta/aseta-kursori! nil))
     (komp/ulos (kartta/kaappaa-hiiri tapahtumat))
     (komp/kuuntelija :esc-painettu
                      (fn [_]
                        (log "optiot: " @optiot)
                        ((:kun-peruttu @optiot)))
                      :enter-painettu
                      #(go (>! tapahtumat {:tyyppi :enter})))
     (fn [_] ;; suljetaan kun-peruttu ja kun-valittu yli
       [:span
        (case @tila
          :ei-valittu "Valitse alkupiste"
          :alku-valittu "Valitse loppupiste"
          "")

        (when-let [virhe @virhe]
          [:div.virhe virhe])]))))
