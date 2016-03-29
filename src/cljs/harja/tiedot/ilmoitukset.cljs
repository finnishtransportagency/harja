(ns harja.tiedot.ilmoitukset
  (:require [reagent.core :refer [atom]]
            [harja.domain.ilmoitukset :refer [+ilmoitustyypit+ kuittaustyypit ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.ilmoituskuittaukset :as kuittausten-tiedot])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

;; FILTTERIT
(defonce ilmoitusnakymassa? (atom false))
(defonce valittu-ilmoitus (atom nil))
(defonce uusi-kuittaus-auki? (atom false))

(defonce kuittaustyyppi-filtterit [:kuittaamaton :vastaanotto :aloitus :lopetus])

(defonce valinnat (reaction {:hallintayksikko (:id @nav/valittu-hallintayksikko)
                             :urakka          (:id @nav/valittu-urakka)
                             :urakoitsija     (:id @nav/valittu-urakoitsija)
                             :urakkatyyppi    (:arvo @nav/valittu-urakkatyyppi)
                             :hoitokausi      @u/valittu-hoitokausi
                             :aikavali        (or @u/valittu-hoitokausi [nil nil])
                             :tyypit          +ilmoitustyypit+
                             :kuittaustyypit  (into #{} kuittaustyyppi-filtterit)
                             :hakuehto        ""
                             :selite          [nil ""]
                             :vain-myohassa?  false
                             :aloituskuittauksen-ajankohta :kaikki}))

(defonce ilmoitushaku (atom 0))

(defn hae-ilmoitukset []
  (go (swap! ilmoitushaku inc)))

(defn jarjesta-ilmoitukset [tulos]
  (reverse (sort-by
             :ilmoitettu
             pvm/ennen?
             (mapv
               (fn [ilmo]
                 (assoc ilmo :kuittaukset
                             (sort-by :kuitattu pvm/ennen? (:kuittaukset ilmo))))
               tulos))))

(defn lisaa-kuittaus-valitulle-ilmoitukselle [kuittaus]
  (let [nykyiset-kuittaukset (:kuittaukset @valittu-ilmoitus)]
    (swap! valittu-ilmoitus assoc :kuittaukset
           (sort-by :kuitattu pvm/ennen?
                    (conj nykyiset-kuittaukset kuittaus)))))

(defonce haetut-ilmoitukset
         (reaction<! [valinnat @valinnat
                      haku @ilmoitushaku]
                     {:odota 100}
           (go
             (if (zero? haku)
               []
               (let [tulos (<! (k/post! :hae-ilmoitukset
                                        (-> valinnat
                                            ;; jos tyyppiä/tilaa ei valittu, ota kaikki
                                            (update :tyypit
                                                    #(if (empty? %) +ilmoitustyypit+ %))
                                            (update :kuittaustyypit
                                                    #(if (empty? %) (into #{} kuittaustyyppi-filtterit) %)))))]

                 (when-not (k/virhe? tulos)
                   (when @valittu-ilmoitus                  ;; Jos on valittuna ilmoitus joka ei ole haetuissa, perutaan valinta
                     (when-not (some #{(:ilmoitusid @valittu-ilmoitus)} (map :ilmoitusid tulos))
                       (reset! valittu-ilmoitus nil)))
                   (jarjesta-ilmoitukset tulos)))))))

(defonce karttataso-ilmoitukset (atom false))

(defonce ilmoitukset-kartalla
         (reaction
           @valittu-ilmoitus
           (when @karttataso-ilmoitukset
             (kartalla-esitettavaan-muotoon
               (map
                 #(assoc % :tyyppi-kartalla (get % :ilmoitustyyppi))
                 @haetut-ilmoitukset)
               @valittu-ilmoitus))))

(defn avaa-uusi-kuittaus! []
  (reset! uusi-kuittaus-auki? true))

(defn sulje-uusi-kuittaus! []
  (reset! uusi-kuittaus-auki? false))

(defn avaa-ilmoitus! [ilmoitus]
  (reset! valittu-ilmoitus ilmoitus)
  (sulje-uusi-kuittaus!)
  (kuittausten-tiedot/alusta-uusi-kuittaus valittu-ilmoitus))

(defn sulje-ilmoitus! []
  (reset! valittu-ilmoitus nil)
  (sulje-uusi-kuittaus!)
  (kuittausten-tiedot/alusta-uusi-kuittaus nil))