(ns harja.palvelin.palvelut.yha-apurit
  "Kevyt YHA-apuri jolla ei riippuvuuksia"
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.paallystys-kyselyt :as paallystys-q]
            [harja.kyselyt.yha :as yha-q]))

(defn lukitse-urakan-yha-sidonta [db urakka-id]
  (log/info "Lukitaan urakan " urakka-id " yha-sidonta.")
  (yha-q/lukitse-urakan-yha-sidonta<! db {:urakka urakka-id}))

(defn tarkista-lahetettavat-kohteet
  "Tarkistaa, että kaikki annetut kohteet ovat siinä tilassa, että ne voidaan lähettää.
   Jos ei ole, heittää poikkeuksen. Vuotta 2020 edeltäviä kohteita ei alkuvuoden kaistauudistuksen jälkeen
   voi enää lähettää."
  [db kohde-idt]
  (doseq [kohde-id kohde-idt]
    (let [paallystysilmoitus  (-> (first (paallystys-q/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella
                                           db
                                           {:paallystyskohde kohde-id}))
                                  (konv/alaviiva->rakenne)
                                  (konv/string-poluista->keyword [[:tekninen-osa :paatos]
                                                                  [:tila]])
                                  (update :vuodet konv/pgarray->vector))]
      (when-not (and (not (empty? (:vuodet paallystysilmoitus)))
                     (every? #(> % 2019) (:vuodet paallystysilmoitus))
                     (= :hyvaksytty (get-in paallystysilmoitus [:tekninen-osa :paatos]))
                     (or (= :valmis (:tila paallystysilmoitus))
                         (= :lukittu (:tila paallystysilmoitus))))
        (throw (IllegalArgumentException. (str "Kohteen " kohde-id " päällystysilmoituksen lähetys ei ole sallittu.")))))))

