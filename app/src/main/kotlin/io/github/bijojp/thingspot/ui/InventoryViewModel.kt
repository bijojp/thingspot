package io.github.bijojp.thingspot.ui

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.data.PhotoStore
import io.github.bijojp.thingspot.domain.CategoryId
import io.github.bijojp.thingspot.domain.Inventory
import io.github.bijojp.thingspot.domain.InventoryRepository
import io.github.bijojp.thingspot.domain.ItemFields
import io.github.bijojp.thingspot.domain.ItemId
import io.github.bijojp.thingspot.domain.LabelCode
import io.github.bijojp.thingspot.domain.PlaceId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** A Place to create, with Places to create inside it (the first-launch starter set). */
data class NewPlace(val name: String, val icon: String, val children: List<NewPlace> = emptyList())

/** One shared view of the inventory for every screen, plus the changes screens can ask for. */
class InventoryViewModel(
    private val repository: InventoryRepository,
    private val photos: PhotoStore,
) : ViewModel() {
    /** Null only until the first snapshot loads. */
    val inventory: StateFlow<Inventory?> = repository.inventory.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val messageChannel = Channel<Int>(Channel.BUFFERED)

    /** Short confirmations ("Item added") as string resources, for the snackbar. */
    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    init {
        // Tidy photo files left behind by deleted Items/Places or abandoned edits.
        viewModelScope.launch { photos.sweep(repository.inventory.first().referencedPhotos()) }
    }

    fun photoFile(name: String): File = photos.file(name)

    fun newCameraFile(): File = photos.newCameraFile()

    fun importPhoto(source: File, onImported: (String) -> Unit) = importPhoto(Uri.fromFile(source), onImported, cleanUp = source)

    fun importPhoto(source: Uri, onImported: (String) -> Unit, cleanUp: File? = null) {
        viewModelScope.launch {
            runCatching { photos.import(source) }
                .onSuccess(onImported)
                .onFailure {
                    Log.w(TAG, "Photo import failed", it)
                    messageChannel.send(R.string.msg_photo_failed)
                }
            cleanUp?.delete()
        }
    }

    fun reportNoCamera() {
        viewModelScope.launch { messageChannel.send(R.string.msg_no_camera) }
    }

    fun addPlace(name: String, icon: String, parentId: PlaceId?, photo: String? = null, onAdded: (PlaceId) -> Unit = {}) =
        perform(R.string.msg_place_added) { onAdded(repository.addPlace(name, icon, parentId, photo)) }

    fun addStarterPlaces(starter: NewPlace) = perform(R.string.msg_starter_added) { addTree(starter, parentId = null) }

    private suspend fun addTree(place: NewPlace, parentId: PlaceId?) {
        val id = repository.addPlace(place.name, place.icon, parentId)
        place.children.forEach { addTree(it, id) }
    }

    fun editPlace(id: PlaceId, name: String, icon: String, photo: String?) =
        perform(R.string.msg_saved) { repository.editPlace(id, name, icon, photo) }

    fun movePlace(id: PlaceId, newParentId: PlaceId?) = perform(R.string.msg_moved) { repository.movePlace(id, newParentId) }

    fun deletePlace(id: PlaceId, itemDestination: PlaceId?) =
        perform(R.string.msg_place_deleted) { repository.deletePlace(id, itemDestination) }

    fun addItem(fields: ItemFields) = perform(R.string.msg_item_added) { repository.addItem(fields) }

    fun editItem(id: ItemId, fields: ItemFields) = perform(R.string.msg_saved) { repository.editItem(id, fields) }

    fun deleteItem(id: ItemId) = perform(R.string.msg_item_deleted) { repository.deleteItem(id) }

    fun addCategory(name: String, color: Long, onAdded: (CategoryId) -> Unit = {}) =
        perform(done = null) { onAdded(repository.addCategory(name, color)) }

    fun editCategory(id: CategoryId, name: String, color: Long) =
        perform(R.string.msg_saved) { repository.editCategory(id, name, color) }

    fun deleteCategory(id: CategoryId) = perform(R.string.msg_category_deleted) { repository.deleteCategory(id) }

    fun linkLabel(code: LabelCode, placeId: PlaceId) = perform(R.string.msg_label_linked) { repository.linkLabel(code, placeId) }

    fun unlinkLabel(code: LabelCode) = perform(R.string.msg_label_unlinked) { repository.unlinkLabel(code) }

    /** Makes [count] fresh Label codes, links them to [placeId] (none for blank Labels), then [onReady]. */
    fun prepareLabels(placeId: PlaceId?, count: Int, onReady: (List<LabelCode>) -> Unit) = perform(done = null) {
        val current = inventory.value
        val codes = generateSequence { LabelCode.random() }
            .filter { current?.placeFor(it) == null }
            .distinct()
            .take(count)
            .toList()
        if (placeId != null) codes.forEach { repository.linkLabel(it, placeId) }
        onReady(codes)
    }

    private fun perform(@StringRes done: Int?, change: suspend () -> Unit) {
        viewModelScope.launch {
            val succeeded = runCatching { change() }
                .onFailure { Log.w(TAG, "Inventory change failed", it) }
                .isSuccess
            (if (succeeded) done else R.string.msg_failed)?.let { messageChannel.send(it) }
        }
    }

    private companion object {
        const val TAG = "ThingSpot"
    }
}
