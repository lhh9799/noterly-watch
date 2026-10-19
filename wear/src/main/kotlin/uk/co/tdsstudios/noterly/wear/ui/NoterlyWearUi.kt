package uk.co.tdsstudios.noterly.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val LIST = "list"
    const val DETAIL = "detail/{id}"
    const val DELETE = "delete/{id}"
    const val ARCHIVE = "archive"
    const val EDIT = "edit"
    const val TIME = "time"
    const val DATE_PICKER = "date"
    const val TIME_PICKER = "clock"
    const val REPEAT = "repeat"

    fun detail(id: String) = "detail/$id"
    fun delete(id: String) = "delete/$id"
}

@Composable
fun NoterlyWearUi(viewModel: WatchViewModel, openItem: MutableStateFlow<String?>, openNew: MutableStateFlow<Boolean>) {
    val navController = rememberSwipeDismissableNavController()
    val view by viewModel.view.collectAsState()
    val connected by viewModel.phoneConnected.collectAsState()
    val itemToOpen by openItem.collectAsState()
    val newRequested by openNew.collectAsState()

    LaunchedEffect(itemToOpen) {
        val id = itemToOpen ?: return@LaunchedEffect
        navController.navigate(Routes.detail(id)) { popUpTo(Routes.LIST) }
        openItem.value = null
    }
    LaunchedEffect(newRequested) {
        if (!newRequested) return@LaunchedEffect
        viewModel.startNew()
        navController.navigate(Routes.EDIT) { popUpTo(Routes.LIST) }
        openNew.value = false
    }

    MaterialTheme {
        AppScaffold {
            SwipeDismissableNavHost(navController = navController, startDestination = Routes.LIST) {
                composable(Routes.LIST) {
                    ListScreen(
                        view = view,
                        phoneConnected = connected,
                        onOpen = { navController.navigate(Routes.detail(it)) },
                        onNew = {
                            viewModel.startNew()
                            navController.navigate(Routes.EDIT)
                        },
                        onArchive = { navController.navigate(Routes.ARCHIVE) },
                    )
                }
                composable(Routes.DETAIL) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    DetailScreen(
                        item = view?.item(id),
                        waitingForPhone = view?.pendingIds?.contains(id) == true,
                        onDone = {
                            viewModel.markDone(id)
                            navController.popBackStack()
                        },
                        onSnooze = {
                            viewModel.snooze(id)
                            navController.popBackStack()
                        },
                        onEdit = { item ->
                            viewModel.startEdit(item)
                            navController.navigate(Routes.EDIT)
                        },
                        onDelete = { navController.navigate(Routes.delete(id)) },
                        onRestore = {
                            viewModel.restore(id)
                            navController.popBackStack()
                        },
                    )
                }
                composable(Routes.DELETE) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    DeleteScreen(
                        onConfirm = {
                            viewModel.delete(id)
                            navController.popBackStack(Routes.LIST, inclusive = false)
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
                composable(Routes.ARCHIVE) {
                    ArchiveScreen(items = view?.archived.orEmpty(), onOpen = { navController.navigate(Routes.detail(it)) })
                }
                composable(Routes.EDIT) {
                    EditScreen(
                        draft = viewModel.draft,
                        onChange = viewModel::updateDraft,
                        onTime = { navController.navigate(Routes.TIME) },
                        onRepeat = { navController.navigate(Routes.REPEAT) },
                        onSave = {
                            viewModel.saveDraft()
                            navController.popBackStack(Routes.LIST, inclusive = false)
                        },
                    )
                }
                composable(Routes.TIME) {
                    TimeChoiceScreen(
                        draft = viewModel.draft,
                        onChosen = { dateTime ->
                            viewModel.updateDraft { it.copy(dateTime = dateTime) }
                            navController.popBackStack()
                        },
                        onPick = { navController.navigate(Routes.DATE_PICKER) },
                    )
                }
                composable(Routes.DATE_PICKER) {
                    DatePickerScreen(
                        draft = viewModel.draft,
                        onPicked = { date ->
                            viewModel.updateDraft { it.copy(dateTime = date.atTime(it.dateTime?.toLocalTime() ?: defaultTime())) }
                            navController.navigate(Routes.TIME_PICKER)
                        },
                    )
                }
                composable(Routes.TIME_PICKER) {
                    TimePickerScreen(
                        draft = viewModel.draft,
                        onPicked = { time ->
                            viewModel.updateDraft { it.copy(dateTime = (it.dateTime?.toLocalDate() ?: java.time.LocalDate.now()).atTime(time)) }
                            navController.popBackStack(Routes.EDIT, inclusive = false)
                        },
                    )
                }
                composable(Routes.REPEAT) {
                    RepeatScreen(
                        repetition = viewModel.draft.repetition,
                        onChange = { repetition -> viewModel.updateDraft { it.copy(repetition = repetition) } },
                        onDone = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun defaultTime(): java.time.LocalTime = java.time.LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
