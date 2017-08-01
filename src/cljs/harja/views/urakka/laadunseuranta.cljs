(ns harja.views.urakka.laadunseuranta
  (:require [reagent.core :refer [atom]]
            [harja.ui.bootstrap :as bs]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta :as urakka-laadunseuranta]
            [harja.views.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.views.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.views.urakka.laadunseuranta.sanktiot :as sanktiot]
            [harja.views.urakka.laadunseuranta.mobiilityokalu :as mobiilityokalu]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.urakka.laadunseuranta.siltatarkastukset :as siltatarkastukset]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.urakka :as urakka]))

(defn valilehti-mahdollinen? [valilehti {:keys [tyyppi sopimustyyppi id] :as urakka}]
  (case valilehti
    :tarkastukset (or (and (oikeudet/urakat-laadunseuranta-tarkastukset id)
                           (not (urakka/vesivaylaurakka? urakka)))
                      (and
                        (istunto/ominaisuus-kaytossa? :vesivayla)
                        (urakka/vesivaylaurakka? urakka)
                        (oikeudet/urakat-laadunseuranta-tarkastukset id)))
    :laatupoikkeamat (or (and (oikeudet/urakat-laadunseuranta-laatupoikkeamat id)
                              (not (urakka/vesivaylaurakka? urakka)))
                         (and
                           (istunto/ominaisuus-kaytossa? :vesivayla)
                           (urakka/vesivaylaurakka? urakka)
                           (oikeudet/urakat-laadunseuranta-laatupoikkeamat id)))
    :sanktiot (or (and
                    (not (urakka/vesivaylaurakka? urakka))
                    (oikeudet/urakat-laadunseuranta-sanktiot id))
                  (and
                    (istunto/ominaisuus-kaytossa? :vesivayla)
                    (urakka/vesivaylaurakka? urakka)
                    (oikeudet/urakat-laadunseuranta-sanktiot id)))
    :siltatarkastukset (and (= :hoito tyyppi)
                            (oikeudet/urakat-laadunseuranta-siltatarkastukset id))
    :mobiilityokalu (not (urakka/vesivaylaurakka? urakka))))

(defn laadunseuranta [ur]
  (komp/luo
    (komp/lippu urakka-laadunseuranta/laadunseurannassa?)
    (fn [{:keys [id tyyppi] :as ur}]
      [bs/tabs
       {:style :tabs :classes "tabs-taso2"
        :active (nav/valittu-valilehti-atom :laadunseuranta)}

       "Tarkastukset" :tarkastukset
       (when (valilehti-mahdollinen? :tarkastukset ur)
         [tarkastukset/tarkastukset {:nakyma tyyppi
                                     :urakka ur}])

       "Laatupoikkeamat" :laatupoikkeamat
       (when (valilehti-mahdollinen? :laatupoikkeamat ur)
         [laatupoikkeamat/laatupoikkeamat {:nakyma tyyppi :urakka ur}])

       (if @tiedot-urakka/yllapidon-urakka? "Sakot ja bonukset" "Sanktiot") :sanktiot
       (when (valilehti-mahdollinen? :sanktiot ur)
         [sanktiot/sanktiot])

       "Siltatarkastukset" :siltatarkastukset
       (when (and (= :hoito tyyppi)
                  (valilehti-mahdollinen? :siltatarkastukset ur)
                  (oikeudet/urakat-laadunseuranta-siltatarkastukset id))
         ^{:key "siltatarkastukset"}
         [siltatarkastukset/siltatarkastukset])

       "Mobiilityökalu" :mobiilityokalu
       ^{:key "mobiilityokalu"}
       (when (valilehti-mahdollinen? :mobiilityokalu ur)
         [mobiilityokalu/mobiilityokalu])])))
