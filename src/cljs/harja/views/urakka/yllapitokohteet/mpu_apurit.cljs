(ns harja.views.urakka.yllapitokohteet.mpu-apurit
  "MPU kustannusten apufunktiot"
  (:require [harja.tiedot.urakka.mpu-kustannukset :as tiedot]
            [harja.fmt :as fmt]
            [harja.ui.lomake :as lomake]
            [harja.ui.protokollat :as protokollat]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.napit :as napit])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn koosta-selitteet
  "Mäppää app staten vectorin ['Teksti'] muotoon {:teksti 'Teksti'}"
  [selitteet]
  (into {}
    (map #(vector (fmt/string-avaimeksi %) %))
    (filter some? selitteet)))


(defn selitehaku 
  "Kopsattu tieliikenneilmoitukset.cljs, tehty funktioksi johon passataan tuck app staten käyttäjien lisäämät selitteet
   Käytetään autofillinä jotka tarjotaan alasvetovalintoihin kun käyttäjä kirjoittaa selitettä"
  [selitteet]
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [selitteet (koosta-selitteet selitteet)
                itemit (if (< (count teksti) 1)
                         (vals selitteet)
                         (filter #(not= (.indexOf (.toLowerCase (val %))
                                                  (.toLowerCase teksti)) -1)
                           selitteet))]
            (vec (sort itemit)))))))


(defn kustannuksen-lisays-lomake [e! {:keys [voi-kirjoittaa? lomake-valinnat
                                             kustannusten-selitteet kayttajien-selitteet]} voi-tallentaa?]
  [:div.overlay-oikealla
   [lomake/lomake
    {:ei-borderia? true
     :voi-muokata? voi-kirjoittaa?
     :tarkkaile-ulkopuolisia-muutoksia? true
     :muokkaa! #(e! (tiedot/->MuokkaaLomaketta %))
     ;; Header
     :header [:div.col-md-12
              [:h2.header-yhteiset {:data-cy "mpu-kustannus-lisays"} "Lisää kustannus"]
              [:hr]]
     ;; Footer
     :footer [:<>
              [:div.muokkaus-modal-napit
               ;; Tallenna
               [napit/tallenna "Tallenna" #(e! (tiedot/->TallennaKustannus lomake-valinnat))  {:disabled (not voi-tallentaa?)
                                                                                               :data-attributes {:data-cy "tallena-mpu-kustannus"}}]
               ;; Peruuta 
               [napit/yleinen-toissijainen "Peruuta" #(e! (tiedot/->SuljeLomake)) {:data-attributes {:data-cy "mpu-kustannus-peruuta"}}]]]}
    
    ;; Tyyppi
    [(lomake/rivi
       {:otsikko "Kustannuksen tyyppi"
        :pakollinen? true
        :rivi-luokka "lomakeryhman-rivi-tausta"
        :validoi [[:ei-tyhja "Valitse tyyppi"]]
        :nimi :kustannus-tyyppi
        :tyyppi :valinta
        :valinnat kustannusten-selitteet
        ::lomake/col-luokka "leveys-kokonainen"})

     ;; Selite 
     (when (some? (:kustannus-tyyppi lomake-valinnat))
       (lomake/rivi
         {:nimi :kustannus-selite
          :otsikko "Selite"
          :validoi (if
                     (= (:kustannus-tyyppi lomake-valinnat) "Muut kustannukset")
                     [[:ei-tyhja "Kirjoita kustannuksen selite"]]
                     nil)
          :tyyppi :haku
          :piilota-checkbox? true
          :piilota-dropdown? true
          :salli-kirjoitus? true
          :hae-kun-yli-n-merkkia 0
          :nayta second :fmt second
          :lahde (selitehaku kayttajien-selitteet)
          :rivi-luokka "lomakeryhman-rivi-tausta"
          ::lomake/col-luokka "leveys-kokonainen"}))

     ;; Määrä 
     (lomake/rivi
       {:otsikko "Kustannus"
        :pakollinen? true
        :rivi-luokka "lomakeryhman-rivi-tausta"
        :nimi :kustannus
        :tyyppi :euro
        :teksti-oikealla "EUR"
        :vayla-tyyli? true
        :validoi [[:ei-tyhja "Syötä kustannusarvo"]]
        ::lomake/col-luokka "maara-valinnat"})]
    lomake-valinnat]])


(defn muut-kustannukset-grid [{:keys [haku-kaynnissa? muut-kustannukset 
                                      kustannukset-yhteensa urakka-ajan-kustannukset-yhteensa] :as _app} valittu-vuosi]

  [grid/grid {:tyhja (if haku-kaynnissa?
                       [ajax-loader "Haku käynnissä..."]
                       "Valitulle aikavälille ei löytynyt mitään.")
              :tunniste :id
              :sivuta grid/vakiosivutus
              :voi-kumota? false
              :piilota-toiminnot? true
              :piilota-otsikot? true
              ;; MPU kustannukset yhteenveto 
              ;; Lisätään 2 riviä gridin päätteeksi
              :rivi-jalkeen-fn (fn [_rivit]
                                 [;; Vuosi yhteensä
                                  ^{:luokka "kustannukset-yhteenveto"}
                                  [{:teksti (str valittu-vuosi " Kustannukset yhteensä") :luokka "lihavoitu"}
                                   {}
                                   {:teksti (str (fmt/euro-opt false kustannukset-yhteensa) " €") :tasaa :oikea :luokka "lihavoitu"}]
                                  ;; Urakka-aika yhteensä
                                  ^{:luokka "kustannukset-yhteenveto"}
                                  [{:teksti "Urakka-ajan kustannukset yhteensä" :luokka "lihavoitu"}
                                   {}
                                   {:teksti (str (fmt/euro-opt false urakka-ajan-kustannukset-yhteensa) " €") :tasaa :oikea :luokka "lihavoitu"}]])}

   [{:tyyppi :string
     :nimi :kustannustyyppi
     :luokka "text-nowrap"
     :leveys 1}

    {:tyyppi :string
     :nimi :selite
     :luokka "text-nowrap"
     :leveys 1}

    {:tyyppi :euro
     :desimaalien-maara 2
     :nimi :kokonaiskustannus
     :tasaa :oikea
     :luokka "text-nowrap"
     :leveys 1}]
   muut-kustannukset])
