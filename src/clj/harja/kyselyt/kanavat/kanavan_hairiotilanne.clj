(ns harja.kyselyt.kanavat.kanavan-hairiotilanne
  (:require [specql.core :refer [fetch insert! update!]]
            [specql.op :as op]
            [harja.pvm :as pvm]
            [harja.id :as id]

            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [clojure.set :as set]
            [harja.geo :as geo]))

(defn hae-hairiotilanteet [db hakuehdot]
  (fetch db
         ::hairiotilanne/hairiotilanne
         (set/union
           hairiotilanne/perustiedot
           hairiotilanne/viittaus-idt
           hairiotilanne/muokkaustiedot
           hairiotilanne/kuittaajan-tiedot
           hairiotilanne/kohteen-tiedot
           hairiotilanne/kohteenosan-tiedot)
         hakuehdot))

(defn hae-sopimuksen-hairiotilanteet-aikavalilta [db hakuehdot]
  (let [urakka-id (::hairiotilanne/urakka-id hakuehdot)
        sopimus-id (:haku-sopimus-id hakuehdot)
        vikaluokka (:haku-vikaluokka hakuehdot)
        korjauksen-tila (:haku-korjauksen-tila hakuehdot)
        [odotusaika-alku odotusaika-loppu] (:haku-odotusaika-h hakuehdot)
        [korjausaika-alku korjausaika-loppu] (:haku-korjausaika-h hakuehdot)
        paikallinen-kaytto? (:haku-paikallinen-kaytto? hakuehdot)
        [aikavali-alku aikavali-loppu] (:haku-aikavali hakuehdot)]

    (hae-hairiotilanteet db (op/and
                                 (op/or {::muokkaustiedot/poistettu? op/null?}
                                        {::muokkaustiedot/poistettu? false})
                                 (merge
                                   {::hairiotilanne/urakka-id urakka-id}
                                   (when sopimus-id
                                     {::hairiotilanne/sopimus-id sopimus-id})
                                   (when vikaluokka
                                     {::hairiotilanne/vikaluokka vikaluokka})
                                   (when korjauksen-tila
                                     {::hairiotilanne/korjauksen-tila korjauksen-tila})
                                   (when (some? paikallinen-kaytto?)
                                     {::hairiotilanne/paikallinen-kaytto? paikallinen-kaytto?})
                                   (when (and odotusaika-alku odotusaika-loppu)
                                     {::hairiotilanne/odotusaika-h (op/between odotusaika-alku odotusaika-loppu)})
                                   (when (and korjausaika-alku korjausaika-loppu)
                                     {::hairiotilanne/korjausaika-h (op/between korjausaika-alku korjausaika-loppu)})
                                   (when (and aikavali-alku aikavali-loppu)
                                     {::hairiotilanne/havaintoaika (op/between aikavali-alku aikavali-loppu)}))))))

(defn tallenna-hairiotilanne [db kayttaja-id hairiotilanne]
  (let [hairiotilanne (update hairiotilanne ::hairiotilanne/sijainti #(when % (geo/geometry (geo/clj->pg %))))]
    (if (id/id-olemassa? (::hairiotilanne/id hairiotilanne))
     (let [hairiotilanne (assoc hairiotilanne
                           ::muokkaustiedot/muokattu (pvm/nyt)
                           ::muokkaustiedot/muokkaaja-id kayttaja-id)]
       (update! db ::hairiotilanne/hairiotilanne hairiotilanne {::hairiotilanne/id (::hairiotilanne/id hairiotilanne)})
       (first (fetch db ::hairiotilanne/hairiotilanne #{::hairiotilanne/id} {::hairiotilanne/id (::hairiotilanne/id hairiotilanne)})))
     (let [hairiotilanne (assoc hairiotilanne
                           ::hairiotilanne/kuittaaja-id kayttaja-id
                           ::muokkaustiedot/luotu (pvm/nyt)
                           ::muokkaustiedot/luoja-id kayttaja-id)
           lisatty-rivi (insert! db ::hairiotilanne/hairiotilanne hairiotilanne)]
       lisatty-rivi))))
